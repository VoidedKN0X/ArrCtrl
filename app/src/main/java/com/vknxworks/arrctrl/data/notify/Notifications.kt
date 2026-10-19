package com.vknxworks.arrctrl.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vknxworks.arrctrl.MainActivity
import com.vknxworks.arrctrl.R

/** What ArrCtrl can tell you about. Each has its own Android notification channel, so it can be tuned in system settings too. */
enum class NotifyEvent(@StringRes val label: Int, @StringRes val description: Int, val importance: Int) {
    GRABBED(R.string.notify_ev_grabbed, R.string.notify_ev_grabbed_body, NotificationManager.IMPORTANCE_LOW),
    IMPORTED(R.string.notify_ev_imported, R.string.notify_ev_imported_body, NotificationManager.IMPORTANCE_DEFAULT),
    FAILED(R.string.notify_ev_failed, R.string.notify_ev_failed_body, NotificationManager.IMPORTANCE_DEFAULT),
    ATTENTION(R.string.notify_ev_attention, R.string.notify_ev_attention_body, NotificationManager.IMPORTANCE_DEFAULT),
    TORRENT_DONE(R.string.notify_ev_torrent, R.string.notify_ev_torrent_body, NotificationManager.IMPORTANCE_LOW),
    HEALTH(R.string.notify_ev_health, R.string.notify_ev_health_body, NotificationManager.IMPORTANCE_DEFAULT),
    DISK(R.string.notify_ev_disk, R.string.notify_ev_disk_body, NotificationManager.IMPORTANCE_DEFAULT),
    OFFLINE(R.string.notify_ev_offline, R.string.notify_ev_offline_body, NotificationManager.IMPORTANCE_DEFAULT);

    val channel: String get() = "ev_" + name.lowercase()

    companion object {
        fun of(name: String): NotifyEvent? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One notification. [key] identifies the thing it's about, so the same thing replaces its earlier notification.
 * [open] says where a tap goes: "activity", "health", "movie/<serverId>/<id>" or "series/<serverId>/<id>".
 */
data class Alert(val event: NotifyEvent, val key: String, val title: String, val text: String?, val open: String)

object Notifications {
    const val ONGOING_CHANNEL = "ongoing"
    private const val ONGOING_ID = 1
    /** More alerts than this of one kind in a single check become one summary notification. */
    private const val SUMMARY_OVER = 4

    const val EXTRA_OPEN = "com.vknxworks.arrctrl.OPEN"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = NotifyEvent.entries.map { e ->
            NotificationChannel(e.channel, context.getString(e.label), e.importance).apply { description = context.getString(e.description) }
        } + NotificationChannel(ONGOING_CHANNEL, context.getString(R.string.notify_ongoing), NotificationManager.IMPORTANCE_MIN).apply {
            description = context.getString(R.string.notify_ongoing_body)
            setShowBadge(false)
        }
        nm.createNotificationChannels(channels)
    }

    /** Whether the app is allowed to post (Android 13+ asks for permission). */
    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun tap(context: Context, open: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, open)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * What a locked phone shows instead of the real notification: the kind of event only, never which movie,
     * series, torrent, server or error it's about. (Android shows the full notification on a locked screen
     * only if you allowed sensitive notification content there.)
     */
    private fun redacted(context: Context, channel: String, title: String): android.app.Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_arrctrl)
            .setContentTitle(title)
            .build()

    /** Posts [alerts], one per item, or one summary per kind when a check finds many at once. */
    fun post(context: Context, alerts: List<Alert>) {
        if (alerts.isEmpty() || !allowed(context)) return
        ensureChannels(context)
        val nm = NotificationManagerCompat.from(context)
        alerts.groupBy { it.event }.forEach { (event, list) ->
            if (list.size > SUMMARY_OVER) {
                val id = ("summary:" + event.name).hashCode()
                val style = NotificationCompat.InboxStyle()
                list.take(7).forEach { style.addLine(it.title) }
                if (list.size > 7) style.setSummaryText(context.getString(R.string.notify_and_more, list.size - 7))
                val title = context.getString(R.string.notify_summary_title, context.getString(event.label), list.size)
                val n = NotificationCompat.Builder(context, event.channel)
                    .setSmallIcon(R.drawable.ic_stat_arrctrl)
                    .setContentTitle(title)
                    .setContentText(list.joinToString(", ") { it.title })
                    .setStyle(style.setBigContentTitle(title))
                    .setContentIntent(tap(context, if (event == NotifyEvent.HEALTH || event == NotifyEvent.DISK || event == NotifyEvent.OFFLINE) "health" else "activity", id))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(redacted(context, event.channel, title))
                    .setAutoCancel(true)
                    .build()
                runCatching { nm.notify(id, n) }
            } else {
                list.forEach { a ->
                    val id = (a.event.name + ":" + a.key).hashCode()
                    val n = NotificationCompat.Builder(context, event.channel)
                        .setSmallIcon(R.drawable.ic_stat_arrctrl)
                        .setContentTitle(a.title)
                        .setContentText(a.text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(a.text))
                        .setContentIntent(tap(context, a.open, id))
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPublicVersion(redacted(context, event.channel, context.getString(event.label)))
                        .setAutoCancel(true)
                        .build()
                    runCatching { nm.notify(id, n) }
                }
            }
        }
    }

    /** The ongoing "downloading" notification; quiet and not dismissable while something downloads. */
    fun ongoing(context: Context, title: String, lines: List<String>) {
        if (!allowed(context)) return
        ensureChannels(context)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(6).forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(context, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arrctrl)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tap(context, "activity", ONGOING_ID))
            // The title is only a count and speed; the lines with names stay hidden on a locked screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(context, ONGOING_CHANNEL, title))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ONGOING_ID, n) }
    }

    fun cancelOngoing(context: Context) = NotificationManagerCompat.from(context).cancel(ONGOING_ID)
}
