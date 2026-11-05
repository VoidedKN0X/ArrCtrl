package com.vknxworks.arrctrl.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vknxworks.arrctrl.MainActivity
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.notify.Notifications
import com.vknxworks.arrctrl.ui.theme.WidgetDark
import com.vknxworks.arrctrl.ui.theme.WidgetLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** ArrCtrl's Signal colours: dark or light, following the phone's dark theme (no wallpaper colours). */
@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
private suspend fun widgetColors(context: Context): ColorProviders? =
    androidx.glance.material3.ColorProviders(light = WidgetLight, dark = WidgetDark)

/** Opens ArrCtrl at [open]; each target gets its own URI so list rows don't share one PendingIntent. */
private fun openIntent(context: Context, open: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("arrctrl://open/" + Uri.encode(open)))
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(Notifications.EXTRA_OPEN, open)

/** The refresh button on both widgets. */
class RefreshWidgetsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Widgets.refreshNow(context)
    }
}

@Composable
private fun WidgetFrame(context: Context, title: String, updatedAt: Long, open: String, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent(context, open)))) {
                Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (updatedAt > 0) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
            Image(
                ImageProvider(R.drawable.ic_widget_refresh),
                context.getString(R.string.widget_refresh),
                GlanceModifier.size(32.dp).padding(6.dp).clickable(actionRunCallback<RefreshWidgetsAction>()),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

/**
 * ArrCtrl is locked (checked on the main thread, locking first when the lock timeout ran out). The
 * background refresh may keep caching data, but the widgets never show it until ArrCtrl is unlocked.
 */
private suspend fun appLocked(context: Context): Boolean =
    runCatching { withContext(Dispatchers.Main) { context.container.lockedNow() } }.getOrDefault(true)

/** Opens ArrCtrl (to its lock screen) without any target. */
private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** Shown instead of a widget's content while ArrCtrl is locked: no titles, counts or times. Tap opens ArrCtrl. */
@Composable
private fun LockedWidget(context: Context) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp).clickable(actionStartActivity(openAppIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(context.getString(R.string.widget_locked), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(context.getString(R.string.widget_locked_subtitle), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
    }
}

@Composable
private fun Message(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    }
}

// ---------- Upcoming ----------

class UpcomingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = appLocked(context)
        val snapshot = if (locked) WidgetSnapshot() else WidgetStore.load(context)
        val colors = widgetColors(context)
        provideContent {
            val body: @Composable () -> Unit = { if (locked) LockedWidget(context) else Upcoming(context, snapshot) }
            if (colors != null) GlanceTheme(colors) { body() } else GlanceTheme { body() }
        }
    }
}

private sealed interface UpRow {
    data class Day(val label: String) : UpRow
    data class Item(val item: UpcomingItem) : UpRow
}

private fun dayLabel(context: Context, iso: String): String {
    val day = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (day) {
        today -> context.getString(R.string.calendar_today)
        today.plusDays(1) -> context.getString(R.string.calendar_tomorrow)
        else -> day.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMM")))
    }
}

@Composable
private fun Upcoming(context: Context, s: WidgetSnapshot) {
    WidgetFrame(context, context.getString(R.string.widget_upcoming), s.updatedAt, "calendar") {
        when {
            s.updatedAt == 0L -> Message(context.getString(R.string.widget_loading))
            s.noCalendarServers -> Message(context.getString(R.string.widget_no_calendar))
            s.upcoming.isEmpty() -> Message(context.getString(R.string.widget_nothing_upcoming, WidgetStore.DAYS))
            else -> {
                val rows = buildList {
                    var last: String? = null
                    s.upcoming.forEach { u ->
                        if (u.day != last) { add(UpRow.Day(dayLabel(context, u.day))); last = u.day }
                        add(UpRow.Item(u))
                    }
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(rows) { r ->
                        when (r) {
                            is UpRow.Day -> Text(
                                r.label,
                                GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                            is UpRow.Item -> UpcomingRow(context, r.item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(context: Context, u: UpcomingItem) {
    val dot = when (u.status) {
        "MISSING" -> GlanceTheme.colors.error
        "UNMONITORED" -> GlanceTheme.colors.outline
        else -> GlanceTheme.colors.primary
    }
    val detail = u.subtitle?.let { if (u.release == null) it else null } ?: when (u.release) {
        "CINEMA" -> context.getString(R.string.calendar_cinema)
        "DIGITAL" -> context.getString(R.string.calendar_digital)
        "PHYSICAL" -> context.getString(R.string.calendar_physical)
        else -> null
    }
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(openIntent(context, u.open))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(dot)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(u.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
            if (detail != null) Text(detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
        }
        if (u.time != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(u.time, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

// ---------- Downloads ----------

class DownloadsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = appLocked(context)
        val snapshot = if (locked) WidgetSnapshot() else WidgetStore.load(context)
        val colors = widgetColors(context)
        provideContent {
            val body: @Composable () -> Unit = { if (locked) LockedWidget(context) else Downloads(context, snapshot) }
            if (colors != null) GlanceTheme(colors) { body() } else GlanceTheme { body() }
        }
    }
}

@Composable
private fun Downloads(context: Context, s: WidgetSnapshot) {
    val title = if (s.speed > 0) context.getString(R.string.widget_downloads_speed, Format.speed(s.speed)) else context.getString(R.string.widget_downloads)
    WidgetFrame(context, title, s.updatedAt, "activity") {
        when {
            s.updatedAt == 0L -> Message(context.getString(R.string.widget_loading))
            s.noDownloadServers -> Message(context.getString(R.string.widget_no_downloads_server))
            s.downloads.isEmpty() -> Message(context.getString(R.string.widget_nothing_downloading))
            else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                items(s.downloads) { d ->
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(openIntent(context, d.open)))) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOfNotNull(d.title, d.detail).joinToString(" · "),
                                GlanceModifier.defaultWeight(),
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                listOfNotNull(Format.percent(d.progress.toDouble()), d.eta).joinToString(" · "),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = d.progress.coerceIn(0f, 1f),
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                            color = GlanceTheme.colors.primary,
                            backgroundColor = GlanceTheme.colors.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Receivers ----------

/** Shared by both receivers: start periodic refreshes with the first widget, stop them with the last. */
abstract class ArrWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.schedule(context)
        Widgets.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Only this kind is gone; the worker itself stops when no widget of either kind is left.
        Widgets.refreshNow(context)
    }
}

class UpcomingWidgetReceiver : ArrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

class DownloadsWidgetReceiver : ArrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DownloadsWidget()
}
