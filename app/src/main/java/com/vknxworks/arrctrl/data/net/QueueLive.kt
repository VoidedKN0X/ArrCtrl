package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.data.api.Api
import com.vknxworks.arrctrl.data.api.ApiJson
import com.vknxworks.arrctrl.data.api.arr
import com.vknxworks.arrctrl.data.api.int
import com.vknxworks.arrctrl.data.api.objects
import com.vknxworks.arrctrl.data.api.str
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** What one SignalR frame from the server means for the queue. */
enum class LiveEvent { READY, QUEUE_CHANGED, CLOSE, OTHER }

/**
 * The few parts of SignalR's JSON hub protocol the live queue needs (ASP.NET Core SignalR, HubProtocol.md):
 * every message is a JSON object followed by the record separator 0x1E; the client opens with a handshake,
 * the server answers `{}`, and after that sends invocations (type 1), pings (type 6) and close (type 7).
 * Radarr, Sonarr and Lidarr send everything as `receiveMessage({name, body})`, with name `queue`,
 * `queue/details` or `queue/status` when their download queue changed.
 */
object SignalRProtocol {
    const val SEPARATOR = '\u001e'
    const val HANDSHAKE = "{\"protocol\":\"json\",\"version\":1}\u001e"
    const val PING = "{\"type\":6}\u001e"

    /** The events in one WebSocket text frame (which can carry several messages). */
    fun events(frame: String): List<LiveEvent> = frame.split(SEPARATOR).filter { it.isNotBlank() }.map { part ->
        val o = runCatching { ApiJson.parseToJsonElement(part) as? JsonObject }.getOrNull() ?: return@map LiveEvent.OTHER
        when {
            // The handshake answer: an empty object, or one with an error.
            o.isEmpty() -> LiveEvent.READY
            o.containsKey("error") && !o.containsKey("type") -> LiveEvent.CLOSE
            o.int("type") == 7 -> LiveEvent.CLOSE
            o.int("type") == 1 && o.str("target") == "receiveMessage" -> {
                val name = o.arr("arguments").objects().firstOrNull().str("name")
                if (name == "queue" || name == "queue/details" || name == "queue/status") LiveEvent.QUEUE_CHANGED else LiveEvent.OTHER
            }
            else -> LiveEvent.OTHER
        }
    }
}

/**
 * Live "the queue changed" signals from Radarr, Sonarr and Lidarr over the same connection their own web
 * pages use (SignalR on a WebSocket at `/signalr/messages`), so the Activity tab doesn't have to ask every
 * few seconds. Goes through the server's own client: its certificate pin applies, and nothing is sent to
 * another host. Nothing here runs in the background: [run] lives exactly as long as its caller.
 */
class QueueLive(private val http: Http) {
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The id of a server each time it says its queue changed. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    private val _connected = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the servers with a live connection right now. */
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    /** Keeps a live connection to each of [servers] until cancelled, reconnecting when one drops. */
    suspend fun run(servers: List<Server>) {
        coroutineScope { servers.forEach { s -> launch { keep(s) } } }
    }

    private suspend fun keep(server: Server) {
        var wait = FIRST_RETRY_MS
        try {
            while (true) {
                val wasUp = try {
                    session(server)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                _connected.update { it - server.id }
                // A connection that worked starts over quickly; one that never came up backs off.
                wait = if (wasUp) FIRST_RETRY_MS else (wait * 2).coerceAtMost(MAX_RETRY_MS)
                delay(wait)
            }
        } finally {
            _connected.update { it - server.id }
        }
    }

    /** One connection, from negotiate to close. Returns true when it got as far as being ready. */
    private suspend fun session(server: Server): Boolean {
        val base = server.httpUrl ?: return false
        if (server.apiKey.isBlank()) return false
        val client = http.client(server)
        val hub = base.newBuilder().addPathSegments("signalr/messages").build()

        // 1. Negotiate (POST …/negotiate?negotiateVersion=1): the server hands out a connection token.
        val negotiate = hub.newBuilder().addPathSegment("negotiate")
            .addQueryParameter("negotiateVersion", "1")
            .addQueryParameter("access_token", server.apiKey)
            .build()
        val answer = Api.json(client, Request.Builder().url(negotiate).post(ByteArray(0).toRequestBody("text/plain".toMediaType())).build()) as? JsonObject
        val token = answer.str("connectionToken") ?: answer.str("connectionId") ?: return false

        // 2. The WebSocket, 3. the handshake, then messages until it closes.
        val socketUrl = hub.newBuilder().addQueryParameter("id", token).addQueryParameter("access_token", server.apiKey).build()
        val done = CompletableDeferred<Unit>()
        // Set on OkHttp's threads, read here.
        val ready = AtomicBoolean(false)
        val lastHeard = AtomicLong(System.nanoTime())
        val socket = client.newWebSocket(
            Request.Builder().url(socketUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(SignalRProtocol.HANDSHAKE)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastHeard.set(System.nanoTime())
                    for (event in SignalRProtocol.events(text)) {
                        when (event) {
                            LiveEvent.READY -> {
                                ready.set(true)
                                _connected.update { it + server.id }
                            }
                            LiveEvent.QUEUE_CHANGED -> _changes.tryEmit(server.id)
                            LiveEvent.CLOSE -> webSocket.close(1000, null)
                            LiveEvent.OTHER -> Unit
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    done.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    done.complete(Unit)
                }
            },
        )
        try {
            coroutineScope {
                // The server drops a client it hasn't heard from for 30 seconds, and pings every 15 itself.
                val pinger = launch {
                    while (isActive) {
                        delay(PING_MS)
                        if (ready.get()) socket.send(SignalRProtocol.PING)
                        if (System.nanoTime() - lastHeard.get() > SILENCE_NS) socket.cancel()
                    }
                }
                done.await()
                pinger.cancel()
            }
        } finally {
            // Also when the caller was cancelled (the screen was left): let go of the connection at once.
            socket.cancel()
        }
        return ready.get()
    }

    private companion object {
        const val PING_MS = 15_000L
        const val SILENCE_NS = 45_000_000_000L
        const val FIRST_RETRY_MS = 5_000L
        const val MAX_RETRY_MS = 60_000L
    }
}
