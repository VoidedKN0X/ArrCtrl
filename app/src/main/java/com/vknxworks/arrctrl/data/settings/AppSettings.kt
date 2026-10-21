package com.vknxworks.arrctrl.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vknxworks.arrctrl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "arrctrl")

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** How long ArrCtrl can be in the background before the app lock asks again (-1: only after a restart). */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Wallpaper (Material You) colours instead of ArrCtrl's amber. */
    val dynamicColor: Boolean = false,
    /** Movies tab: posters in a grid (true) or a list. */
    val moviesGrid: Boolean = true,
    /** Movies tab sort, a MovieSort name. */
    val moviesSort: String = "TITLE",
    val moviesSortDescending: Boolean = false,
    val seriesGrid: Boolean = true,
    /** Series tab sort, a SeriesSort name. */
    val seriesSort: String = "TITLE",
    val seriesSortDescending: Boolean = false,
    /** Calendar: month grid (true) or agenda list (the default). */
    val calendarMonth: Boolean = false,
    /** Calendar: ALL, MOVIES or SERIES. */
    val calendarKind: String = "ALL",
    val calendarMonitoredOnly: Boolean = true,
    /** System health warns when a disk has less than this percentage free. */
    val diskWarnPercent: Int = 10,
    /** Master switch for notifications. Off until you turn it on. */
    val notifyEnabled: Boolean = false,
    /** Minutes between background checks; 0 = only when a push wakes the app. */
    val notifyInterval: Int = 30,
    /** NotifyEvent names that are switched on. */
    val notifyEvents: Set<String> = DEFAULT_NOTIFY_EVENTS,
    /** Server ids you don't want notifications from. */
    val notifyMuted: Set<String> = emptySet(),
    /** Ongoing notification with active downloads (off by default). */
    val notifyOngoing: Boolean = false,
    /** Instant checks through ntfy. */
    val pushEnabled: Boolean = false,
    /** The push address (on your ntfy server) your servers post to; given by ntfy. */
    val pushEndpoint: String? = null,
    /** App lock: when it asks again after ArrCtrl was in the background. */
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** "Hide in screenshots": FLAG_SECURE on the app's windows (screenshots, recordings and the Recents preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the Recents view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
)

/** Events notified out of the box; see NotifyEvent. */
val DEFAULT_NOTIFY_EVENTS = setOf("IMPORTED", "FAILED", "ATTENTION", "HEALTH", "DISK")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val moviesGrid = booleanPreferencesKey("movies_grid")
        val moviesSort = stringPreferencesKey("movies_sort")
        val moviesDesc = booleanPreferencesKey("movies_sort_desc")
        val seriesGrid = booleanPreferencesKey("series_grid")
        val seriesSort = stringPreferencesKey("series_sort")
        val seriesDesc = booleanPreferencesKey("series_sort_desc")
        val calMonth = booleanPreferencesKey("calendar_month")
        val calKind = stringPreferencesKey("calendar_kind")
        val calMonitored = booleanPreferencesKey("calendar_monitored_only")
        val diskWarn = intPreferencesKey("disk_warn_percent")
        val notify = booleanPreferencesKey("notify_enabled")
        val notifyInterval = intPreferencesKey("notify_interval")
        val notifyEvents = stringSetPreferencesKey("notify_events")
        val notifyMuted = stringSetPreferencesKey("notify_muted")
        val notifyOngoing = booleanPreferencesKey("notify_ongoing")
        val push = booleanPreferencesKey("push_enabled")
        val pushEndpoint = stringPreferencesKey("push_endpoint")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        /** The earlier single "hide in screenshots and recent apps" switch; an "on" turns on both new ones. */
        val legacyHide = booleanPreferencesKey("hide_content")
    }

    init {
        // ArrCtrl 1.0.4 removed the optional anonymous stats. Delete the two settings they used (their
        // keys start with "usage_"), so nothing from them stays on the phone after the update.
        scope.launch {
            runCatching {
                context.appDataStore.edit { p ->
                    p.asMap().keys.filter { it.name.startsWith("usage_") }.forEach { p.remove(booleanPreferencesKey(it.name)) }
                }
            }
        }
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        moviesGrid = this[K.moviesGrid] ?: true,
        moviesSort = this[K.moviesSort] ?: "TITLE",
        moviesSortDescending = this[K.moviesDesc] ?: false,
        seriesGrid = this[K.seriesGrid] ?: true,
        seriesSort = this[K.seriesSort] ?: "TITLE",
        seriesSortDescending = this[K.seriesDesc] ?: false,
        calendarMonth = this[K.calMonth] ?: false,
        calendarKind = this[K.calKind] ?: "ALL",
        calendarMonitoredOnly = this[K.calMonitored] ?: true,
        diskWarnPercent = this[K.diskWarn] ?: 10,
        notifyEnabled = this[K.notify] ?: false,
        notifyInterval = this[K.notifyInterval] ?: 30,
        notifyEvents = this[K.notifyEvents] ?: DEFAULT_NOTIFY_EVENTS,
        notifyMuted = this[K.notifyMuted] ?: emptySet(),
        notifyOngoing = this[K.notifyOngoing] ?: false,
        pushEnabled = this[K.push] ?: false,
        pushEndpoint = this[K.pushEndpoint],
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        hideScreenshots = this[K.hideScreenshots] ?: this[K.legacyHide] ?: false,
        hideRecents = this[K.hideRecents] ?: this[K.legacyHide] ?: false,
    )

    /** Every stored change, without the placeholder value [settings] starts with. */
    val changes: kotlinx.coroutines.flow.Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    /** Reads the stored settings directly; for background work, where [settings] may not have loaded yet. */
    suspend fun current(): AppSettings = context.appDataStore.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.moviesGrid] = s.moviesGrid
            p[K.moviesSort] = s.moviesSort
            p[K.moviesDesc] = s.moviesSortDescending
            p[K.seriesGrid] = s.seriesGrid
            p[K.seriesSort] = s.seriesSort
            p[K.seriesDesc] = s.seriesSortDescending
            p[K.calMonth] = s.calendarMonth
            p[K.calKind] = s.calendarKind
            p[K.calMonitored] = s.calendarMonitoredOnly
            p[K.diskWarn] = s.diskWarnPercent
            p[K.notify] = s.notifyEnabled
            p[K.notifyInterval] = s.notifyInterval
            p[K.notifyEvents] = s.notifyEvents
            p[K.notifyMuted] = s.notifyMuted
            p[K.notifyOngoing] = s.notifyOngoing
            p[K.push] = s.pushEnabled
            if (s.pushEndpoint != null) p[K.pushEndpoint] = s.pushEndpoint else p.remove(K.pushEndpoint)
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p.remove(K.legacyHide)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
