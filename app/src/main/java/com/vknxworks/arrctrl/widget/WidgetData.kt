package com.vknxworks.arrctrl.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.TorrentKind
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.calendar.CalendarLoader
import com.vknxworks.arrctrl.ui.calendar.EntryKind
import com.vknxworks.arrctrl.ui.calendar.EntryStatus
import com.vknxworks.arrctrl.ui.calendar.MovieRelease
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** One calendar entry as the Upcoming widget shows it. */
@Serializable
data class UpcomingItem(
    /** ISO date (local) the entry falls on. */
    val day: String,
    /** "20:00" for episodes; null for movie release days. */
    val time: String?,
    val title: String,
    val subtitle: String?,
    /** An EntryStatus name. */
    val status: String,
    /** A MovieRelease name for movies. */
    val release: String? = null,
    /** Where a tap goes (see Alert.open). */
    val open: String,
)

/** One active download as the Downloads widget shows it. */
@Serializable
data class DownloadItem(val title: String, val detail: String?, val progress: Float, val eta: String?, val open: String)

/** What the widgets show, saved so they can redraw instantly; refreshed by [WidgetWorker]. */
@Serializable
data class WidgetSnapshot(
    val updatedAt: Long = 0,
    val upcoming: List<UpcomingItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    /** Combined qBittorrent download speed, bytes per second. */
    val speed: Long = 0,
    /** True when no Radarr/Sonarr (upcoming) or download source is set up. */
    val noCalendarServers: Boolean = false,
    val noDownloadServers: Boolean = false,
    /** Servers that couldn't be reached during the last refresh. */
    val failures: Int = 0,
)

object WidgetStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(context: Context) = File(context.filesDir, "widgets.json")

    fun load(context: Context): WidgetSnapshot =
        runCatching { json.decodeFromString(WidgetSnapshot.serializer(), file(context).readText()) }.getOrDefault(WidgetSnapshot())

    fun save(context: Context, s: WidgetSnapshot) {
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(WidgetSnapshot.serializer(), s))
        tmp.renameTo(f)
    }

    /** Days ahead the Upcoming widget covers. */
    const val DAYS = 14

    /** Fetches fresh data from every server. */
    suspend fun fetch(context: Context): WidgetSnapshot = coroutineScope {
        val c = context.container
        val servers = c.servers.current()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val upcoming = async {
            CalendarLoader(c).load(servers, today, today.plusDays(DAYS.toLong()), monitoredOnly = true)
        }
        val queues = servers.filter { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }.map { s ->
            async { s to runCatching { c.arr(s).queue() } }
        }
        val torrents = servers.filter { it.type == ServiceType.QBITTORRENT }.map { s ->
            async {
                s to runCatching {
                    val api = c.qbit(s)
                    val st = QbitApi.SyncState()
                    api.sync(st)
                    api.torrents(st) to api.transfer(st).dlSpeed
                }
            }
        }
        val (entries, calErrors) = upcoming.await()
        val q = queues.awaitAll()
        val t = torrents.awaitAll()
        (q.map { it.second.exceptionOrNull() } + t.map { it.second.exceptionOrNull() }).forEach { if (it is CancellationException) throw it }

        val up = entries
            // Downloaded episodes and past cinema dates aren't "upcoming" any more.
            .filter { it.status != EntryStatus.DOWNLOADED && !(it.release == MovieRelease.CINEMA && it.day.isBefore(today)) }
            .take(40)
            .map { e ->
                UpcomingItem(
                    day = e.day.toString(),
                    time = if (e.kind == EntryKind.EPISODE) e.at.atZone(zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) else null,
                    title = e.title,
                    subtitle = e.subtitle,
                    status = e.status.name,
                    release = e.release?.name,
                    open = when {
                        e.movieId != null -> "movie/${e.server.id}/${e.movieId}"
                        e.seriesId != null -> "series/${e.server.id}/${e.seriesId}"
                        else -> "calendar"
                    },
                )
            }

        val queueItems = q.flatMap { it.second.getOrNull().orEmpty() }.filter { it.sizeLeft > 0 && it.status != "completed" }
        val downloading = t.flatMap { it.second.getOrNull()?.first.orEmpty() }.filter { it.kind == TorrentKind.DOWNLOADING || it.kind == TorrentKind.STALLED || it.kind == TorrentKind.QUEUED && it.progress < 1.0 }
        // The *arr queue has the names people recognise; torrents only fill in when there's no queue.
        val dl = if (queueItems.isNotEmpty()) {
            queueItems.sortedByDescending { it.progress }.map { i ->
                DownloadItem(i.title, i.detail, i.progress, Format.eta(i.etaSeconds), "activity")
            }
        } else {
            downloading.sortedByDescending { it.progress }.map { i ->
                DownloadItem(i.name, null, i.progress.toFloat(), Format.eta(i.eta), "activity")
            }
        }

        WidgetSnapshot(
            updatedAt = System.currentTimeMillis(),
            upcoming = up,
            downloads = dl.take(20),
            speed = t.sumOf { it.second.getOrNull()?.second ?: 0L },
            noCalendarServers = servers.none { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR },
            noDownloadServers = servers.none { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR || it.type == ServiceType.QBITTORRENT },
            failures = calErrors.size + t.count { it.second.isFailure } +
                q.count { (s, r) -> r.isFailure && s !in calErrors.keys },
        )
    }
}

/** Refreshes both widgets. While something downloads and a Downloads widget is placed, refreshes again in two minutes. */
class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Widgets.anyPlaced(ctx)) {
            // The last widget was removed: stop the periodic refresh.
            Widgets.cancelPeriodic(ctx)
            return Result.success()
        }
        val snapshot = try {
            WidgetStore.fetch(ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (snapshot != null) WidgetStore.save(ctx, snapshot)
        Widgets.redraw(ctx)
        if (snapshot != null && snapshot.downloads.isNotEmpty() && Widgets.downloadsPlaced(ctx)) Widgets.refreshSoon(ctx)
        return Result.success()
    }
}

object Widgets {
    private const val PERIODIC = "arrctrl-widgets"
    private const val NOW = "arrctrl-widgets-now"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun downloadsPlaced(context: Context): Boolean =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(DownloadsWidget::class.java).isNotEmpty() }.getOrDefault(false)

    suspend fun anyPlaced(context: Context): Boolean = downloadsPlaced(context) ||
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(UpcomingWidget::class.java).isNotEmpty() }.getOrDefault(false)

    suspend fun redraw(context: Context) {
        runCatching { UpcomingWidget().updateAll(context) }
        runCatching { DownloadsWidget().updateAll(context) }
    }

    /** Keeps widgets fresh every 30 minutes while any is on the home screen. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun refreshNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<WidgetWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun refreshSoon(context: Context) {
        val req = OneTimeWorkRequestBuilder<WidgetWorker>().setInitialDelay(2, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}
