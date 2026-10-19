package com.vknxworks.arrctrl.data.notify

import android.content.Context
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.HistoryKind
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.data.settings.AppSettings
import com.vknxworks.arrctrl.ui.more.Severity
import com.vknxworks.arrctrl.ui.more.checkHealth
import com.vknxworks.arrctrl.ui.servers.resultText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Looks at every server, compares with what it saw last time and works out what's new.
 * The first look at a server only records a starting point, so you aren't flooded with old events.
 */
class NotifyChecker(private val c: AppContainer) {
    private val context: Context get() = c.context
    private val prefs = c.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** What was seen on one server at the last check. */
    @Serializable
    data class Seen(
        val lastHistoryId: Int = 0,
        val attention: Set<String> = emptySet(),
        val health: Set<String> = emptySet(),
        val lowDisks: Set<String> = emptySet(),
        val incomplete: Set<String> = emptySet(),
        val offline: Boolean = false,
    )

    private fun load(id: String): Seen? = prefs.getString("seen_$id", null)?.let { runCatching { json.decodeFromString(Seen.serializer(), it) }.getOrNull() }
    private fun save(id: String, s: Seen) = prefs.edit().putString("seen_$id", json.encodeToString(Seen.serializer(), s)).apply()

    /** Runs one check and posts what's new. Returns the alerts (already filtered by your settings). */
    suspend fun check(): List<Alert> {
        val settings = c.settings.current()
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        if (!settings.notifyEnabled) return emptyList()
        val all = c.servers.current()
        // Forget servers that were removed.
        val ids = all.map { it.id }.toSet()
        prefs.all.keys.filter { it.startsWith("seen_") && it.removePrefix("seen_") !in ids }.forEach { prefs.edit().remove(it).apply() }

        val servers = all.filter { it.id !in settings.notifyMuted }
        val health = checkHealth(c, servers.filter { it.type != ServiceType.QBITTORRENT }).associateBy { it.server.id }
        val alerts = coroutineScope { servers.map { s -> async { checkServer(s, settings, health[s.id]) } }.awaitAll().flatten() }
        val wanted = settings.notifyEvents.mapNotNull { NotifyEvent.of(it) }.toSet()
        val out = alerts.filter { it.event in wanted }
        Notifications.post(context, out)
        return out
    }

    private suspend fun checkServer(s: Server, settings: AppSettings, h: com.vknxworks.arrctrl.ui.more.ServerHealth?): List<Alert> {
        val before = load(s.id)
        val first = before == null
        var seen = before ?: Seen()
        val alerts = mutableListOf<Alert>()

        // Health, disks and reachability (everything but qBittorrent comes from System health's check).
        if (h != null) {
            val offline = h.failure != null
            if (offline && !seen.offline) {
                alerts += Alert(NotifyEvent.OFFLINE, s.id, context.getString(R.string.notify_offline_title, s.name), resultText(context, h.failure!!, s.type), "health")
            }
            seen = seen.copy(offline = offline)
            if (!offline) {
                val problems = h.problems.filter { it.severity != Severity.NOTICE }
                val keys = problems.associateBy { (it.source ?: "") + "|" + it.message }
                keys.filterKeys { it !in seen.health }.values.forEach { p ->
                    alerts += Alert(NotifyEvent.HEALTH, s.id + "|" + p.message, context.getString(R.string.notify_health_title, s.name), p.message, "health")
                }
                val low = h.disks.filter { it.total > 0 && it.freeFraction * 100 < settings.diskWarnPercent }
                low.filter { it.path !in seen.lowDisks }.forEach { d ->
                    alerts += Alert(
                        NotifyEvent.DISK, s.id + "|" + d.path,
                        context.getString(R.string.notify_disk_title, d.label ?: d.path),
                        context.getString(R.string.notify_disk_text, Format.bytes(d.free), Format.bytes(d.total), s.name),
                        "health",
                    )
                }
                seen = seen.copy(health = keys.keys, lowDisks = low.map { it.path }.toSet())
            }
        }

        try {
            when (s.type) {
                ServiceType.RADARR, ServiceType.SONARR -> {
                    val arr = c.arr(s)
                    val (items, _) = arr.history(1, 50)
                    val newest = items.maxOfOrNull { it.id } ?: seen.lastHistoryId
                    items.filter { it.id > seen.lastHistoryId }.sortedBy { it.id }.forEach { hi ->
                        val event = when (hi.kind) {
                            HistoryKind.GRABBED -> NotifyEvent.GRABBED
                            HistoryKind.IMPORTED -> NotifyEvent.IMPORTED
                            HistoryKind.FAILED -> NotifyEvent.FAILED
                            else -> null
                        } ?: return@forEach
                        val name = listOfNotNull(hi.title, hi.detail).joinToString(" · ")
                        val title = context.getString(
                            when (event) {
                                NotifyEvent.GRABBED -> R.string.notify_grabbed_title
                                NotifyEvent.IMPORTED -> R.string.notify_imported_title
                                else -> R.string.notify_failed_title
                            },
                            name,
                        )
                        val text = listOfNotNull(if (event == NotifyEvent.FAILED) hi.reason else null, hi.quality, hi.release.takeIf { event != NotifyEvent.IMPORTED }, s.name)
                            .joinToString(" · ")
                        val kind = if (s.type == ServiceType.SONARR) "series" else "movie"
                        alerts += Alert(event, "${s.id}:${hi.id}", title, text, hi.mediaId?.let { "$kind/${s.id}/$it" } ?: "activity")
                    }
                    val warnings = arr.queue().filter { it.hasWarning }
                    val keys = warnings.associateBy { it.downloadId ?: it.id.toString() }
                    keys.filterKeys { it !in seen.attention }.values.forEach { q ->
                        alerts += Alert(
                            NotifyEvent.ATTENTION, "${s.id}:${q.downloadId ?: q.id}",
                            context.getString(R.string.notify_attention_title, listOfNotNull(q.title, q.detail).joinToString(" · ")),
                            listOfNotNull(q.errorMessage ?: q.messages.firstOrNull(), s.name).joinToString(" · "),
                            "activity",
                        )
                    }
                    seen = seen.copy(lastHistoryId = maxOf(newest, seen.lastHistoryId), attention = keys.keys)
                }
                ServiceType.QBITTORRENT -> {
                    val api = c.qbit(s)
                    val state = QbitApi.SyncState()
                    val torrents = try {
                        api.sync(state)
                        api.torrents(state)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (!seen.offline) {
                            // A plain reason, never the raw error (it can contain the server's address).
                            alerts += Alert(NotifyEvent.OFFLINE, s.id, context.getString(R.string.notify_offline_title, s.name), resultText(context, e.toTestResult(), s.type), "activity")
                        }
                        save(s.id, seen.copy(offline = true))
                        return if (first) emptyList() else alerts
                    }
                    val done = torrents.filter { it.progress >= 1.0 && it.hash in seen.incomplete }
                    done.forEach { t ->
                        alerts += Alert(NotifyEvent.TORRENT_DONE, "${s.id}:${t.hash}", context.getString(R.string.notify_torrent_title, t.name), listOfNotNull(Format.bytes(t.size), s.name).joinToString(" · "), "activity")
                    }
                    seen = seen.copy(incomplete = torrents.filter { it.progress < 1.0 }.map { it.hash }.toSet(), offline = false)
                }
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Reachability is reported through the health check above; keep what we knew.
        }

        save(s.id, seen)
        return if (first) emptyList() else alerts
    }

    companion object {
        const val PREFS = "notify_state"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_LAST_PUSH = "last_push"

        fun lastCheck(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0)
        fun lastPush(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_PUSH, 0)
        fun markPush(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_PUSH, System.currentTimeMillis()).apply()

        /** Starts every server from scratch, e.g. after notifications are turned on, so old events aren't reported. */
        fun reset(context: Context) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit().apply { p.all.keys.filter { it.startsWith("seen_") }.forEach { remove(it) } }.apply()
        }
    }
}
