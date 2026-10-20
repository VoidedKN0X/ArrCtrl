package com.vknxworks.arrctrl.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.TorrentKind
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.data.settings.AppSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** One check of every server (periodic, after a push, or "Check now"). */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        // A push usually arrives the moment Radarr or Sonarr acts; give it a moment to write its history.
        if (inputData.getBoolean(KEY_PUSH, false)) delay(3_000)
        try {
            NotifyChecker(c).check()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Never retry in a loop; the next check or push tries again.
        }
        if (c.settings.current().let { it.notifyEnabled && it.notifyOngoing }) NotifySchedule.kickOngoing(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_PUSH = "push"
    }
}

/** Keeps the ongoing "downloading" notification fresh, about once a minute, until nothing is downloading. */
class OngoingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.notifyEnabled || !s.notifyOngoing) {
            Notifications.cancelOngoing(applicationContext)
            return Result.success()
        }
        val active = try {
            Ongoing.update(applicationContext, s)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        if (active) NotifySchedule.continueOngoing(applicationContext)
        return Result.success()
    }
}

/** Builds the ongoing notification from every download queue and qBittorrent. */
object Ongoing {
    /** Returns true while something is downloading. */
    suspend fun update(context: Context, s: AppSettings): Boolean = coroutineScope {
        val c = context.container
        val servers = c.servers.current().filter { it.id !in s.notifyMuted }
        val queues = servers.filter { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }.map { srv ->
            async { runCatching { c.arr(srv).queue() }.getOrDefault(emptyList()) }
        }
        val torrents = servers.filter { it.type == ServiceType.QBITTORRENT }.map { srv ->
            async {
                runCatching {
                    val api = c.qbit(srv)
                    val st = QbitApi.SyncState()
                    api.sync(st)
                    api.torrents(st)
                }.getOrDefault(emptyList())
            }
        }
        val queue = queues.awaitAll().flatten().filter { it.status == "downloading" && it.sizeLeft > 0 }
        val downloading = torrents.awaitAll().flatten().filter { it.kind == TorrentKind.DOWNLOADING }
        val speed = downloading.sumOf { it.dlSpeed }
        // Prefer the *arr queue (titles people recognise); fall back to torrent names.
        val lines = if (queue.isNotEmpty()) {
            queue.sortedByDescending { it.progress }.map { q ->
                context.getString(R.string.notify_ongoing_line, listOfNotNull(q.title, q.detail).joinToString(" · "), (q.progress * 100).toInt())
            }
        } else {
            downloading.sortedByDescending { it.progress }.map { t -> context.getString(R.string.notify_ongoing_line, t.name, (t.progress * 100).toInt()) }
        }
        val count = maxOf(queue.size, downloading.size)
        if (count == 0) {
            Notifications.cancelOngoing(context)
            return@coroutineScope false
        }
        val title = if (speed > 0) context.resources.getQuantityString(R.plurals.notify_ongoing_title_speed, count, count, Format.speed(speed))
        else context.resources.getQuantityString(R.plurals.notify_ongoing_title, count, count)
        Notifications.ongoing(context, title, lines)
        true
    }
}

object NotifySchedule {
    private const val PERIODIC = "arrctrl-check"
    private const val NOW = "arrctrl-check-now"
    private const val ONGOING = "arrctrl-ongoing"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Applies the notification settings: periodic checks, ongoing updates and the registration with ntfy. */
    fun apply(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (s.notifyEnabled && s.notifyInterval > 0) {
            val req = PeriodicWorkRequestBuilder<CheckWorker>(s.notifyInterval.toLong(), TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(PERIODIC)
        }
        if (s.notifyEnabled && s.notifyOngoing) {
            kickOngoing(context)
        } else {
            wm.cancelUniqueWork(ONGOING)
            Notifications.cancelOngoing(context)
        }
        // Earlier versions registered through a push library and could use other push apps. With ntfy the
        // registration (and so the push address) is kept; with any other push app it's removed there, and ArrCtrl
        // registers with ntfy instead if it's installed, or turns push off until it is.
        val previous = UnifiedPush.migrateFromLibrary(context)
        if (s.pushEnabled && previous != null && previous != UnifiedPush.NTFY) {
            val c = context.container
            if (UnifiedPush.isNtfyInstalled(context)) {
                c.appScope.launch { c.settings.update { it.copy(pushEndpoint = null) } }
            } else {
                c.appScope.launch { c.settings.update { it.copy(pushEnabled = false, pushEndpoint = null) } }
                return
            }
        }
        // Registering again at start-up recovers from a reinstalled ntfy; with the same token ntfy keeps the address.
        if (s.notifyEnabled && s.pushEnabled) {
            runCatching { UnifiedPush.register(context, context.getString(R.string.app_name)) }
        }
    }

    /** Checks straight away (a push woke us, or you tapped Check now). Checks queue up rather than cancel each other. */
    fun checkNow(context: Context, push: Boolean) {
        val req = OneTimeWorkRequestBuilder<CheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(CheckWorker.KEY_PUSH to push))
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Starts ongoing updates unless they're already running. */
    fun kickOngoing(context: Context) {
        val req = OneTimeWorkRequestBuilder<OngoingWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONGOING, ExistingWorkPolicy.KEEP, req)
    }

    /** The next ongoing update, a minute from now. */
    fun continueOngoing(context: Context) {
        val req = OneTimeWorkRequestBuilder<OngoingWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONGOING, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}
