package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.data.api.ApiJson
import com.vknxworks.arrctrl.data.api.arr
import com.vknxworks.arrctrl.data.api.int
import com.vknxworks.arrctrl.data.api.objects
import com.vknxworks.arrctrl.data.api.str
import kotlinx.serialization.json.JsonObject

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
