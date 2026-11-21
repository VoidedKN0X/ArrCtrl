package com.vknxworks.arrctrl.data.settings

import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.data.server.Urls
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an encrypted configuration backup holds (see [BackupCrypto] for the file around it): the app's
 * settings for its look and lists, and the list of servers with their type, name, address and, for a
 * qBittorrent sign-in, the user name.
 *
 * Never in the backup, and never changed by a restore: API keys, passwords, trusted certificate
 * fingerprints, the app lock and its PIN, "Lock again", "Hide in screenshots", "Hide in recent apps",
 * and the notification and ntfy settings. A restore doesn't add a server by itself either: servers that
 * aren't on this phone are offered one by one, and each goes through the normal "Add server" screen,
 * where its key is entered and the usual checks (unencrypted address, self-signed certificate) apply.
 */
@Serializable
data class ConfigBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** ArrCtrl version that wrote the backup, for information. */
    val appVersion: String? = null,
    /** ISO time the backup was made. */
    val createdAt: String? = null,
    val settings: BackupSettings = BackupSettings(),
    val servers: List<BackupServer> = emptyList(),
) {
    companion object {
        const val FORMAT = "arrctrl-config"
        const val VERSION = 1
    }
}

/** Every field is optional, so backups from older or newer versions still restore what they can. */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val dynamicColor: Boolean? = null,
    val moviesGrid: Boolean? = null,
    val moviesSort: String? = null,
    val moviesSortDescending: Boolean? = null,
    val seriesGrid: Boolean? = null,
    val seriesSort: String? = null,
    val seriesSortDescending: Boolean? = null,
    val calendarMonth: Boolean? = null,
    val calendarKind: String? = null,
    val calendarMonitoredOnly: Boolean? = null,
    val diskWarnPercent: Int? = null,
)

/** A server without its secrets. [hadKey], [hadPassword] and [hadCertificate] only say what it had. */
@Serializable
data class BackupServer(
    val type: String = "",
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val hadKey: Boolean = false,
    val hadPassword: Boolean = false,
    val hadCertificate: Boolean = false,
)

/** Thrown for content that isn't an ArrCtrl configuration, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object ConfigBackups {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    // The names the app's own menus can store (MovieSort, SeriesSort, KindFilter); anything else is skipped.
    private val MOVIE_SORTS = setOf("TITLE", "YEAR", "ADDED", "RELEASE", "SIZE", "RATING")
    private val SERIES_SORTS = setOf("TITLE", "YEAR", "ADDED", "NEXT_AIRING", "PREVIOUS_AIRING", "SIZE", "PROGRESS")
    private val CALENDAR_KINDS = setOf("ALL", "MOVIES", "SERIES", "MUSIC")
    private val DISK_WARN = setOf(5, 10, 15, 20, 25)
    private const val MAX_SERVERS = 100

    fun export(s: AppSettings, servers: List<Server>, appVersion: String, now: String): String = json.encodeToString(
        ConfigBackup.serializer(),
        ConfigBackup(
            appVersion = appVersion,
            createdAt = now,
            settings = BackupSettings(
                theme = s.theme.name,
                dynamicColor = s.dynamicColor,
                moviesGrid = s.moviesGrid,
                moviesSort = s.moviesSort,
                moviesSortDescending = s.moviesSortDescending,
                seriesGrid = s.seriesGrid,
                seriesSort = s.seriesSort,
                seriesSortDescending = s.seriesSortDescending,
                calendarMonth = s.calendarMonth,
                calendarKind = s.calendarKind,
                calendarMonitoredOnly = s.calendarMonitoredOnly,
                diskWarnPercent = s.diskWarnPercent,
            ),
            servers = servers.map { srv ->
                BackupServer(
                    type = srv.type.id,
                    name = srv.name,
                    url = srv.url,
                    username = srv.username,
                    hadKey = srv.apiKey.isNotBlank(),
                    hadPassword = srv.password.isNotBlank(),
                    hadCertificate = srv.certSha256.isNotBlank(),
                )
            },
        ),
    )

    /** Reads and checks the decrypted content of a backup. Servers with an unknown type or a bad address are left out. */
    fun parse(text: String): ConfigBackup {
        val b = runCatching { json.decodeFromString(ConfigBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        if (b.format != ConfigBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > ConfigBackup.VERSION) throw BackupFormatException(newer = true)
        val servers = b.servers.take(MAX_SERVERS).mapNotNull { s ->
            val type = ServiceType.of(s.type) ?: return@mapNotNull null
            val url = Urls.normalise(s.url) ?: return@mapNotNull null
            s.copy(type = type.id, url = url, name = s.name.take(80), username = s.username.take(200))
        }
        return b.copy(servers = servers)
    }

    /**
     * [cur] with the settings from the backup. A value the backup doesn't have, or one the app's menus
     * don't offer, leaves the current one. The app lock, the privacy switches and notifications are never touched.
     */
    fun apply(cur: AppSettings, s: BackupSettings): AppSettings = cur.copy(
        theme = s.theme?.let { n -> ThemeChoice.entries.firstOrNull { it.name == n } } ?: cur.theme,
        dynamicColor = s.dynamicColor ?: cur.dynamicColor,
        moviesGrid = s.moviesGrid ?: cur.moviesGrid,
        moviesSort = s.moviesSort?.takeIf { it in MOVIE_SORTS } ?: cur.moviesSort,
        moviesSortDescending = s.moviesSortDescending ?: cur.moviesSortDescending,
        seriesGrid = s.seriesGrid ?: cur.seriesGrid,
        seriesSort = s.seriesSort?.takeIf { it in SERIES_SORTS } ?: cur.seriesSort,
        seriesSortDescending = s.seriesSortDescending ?: cur.seriesSortDescending,
        calendarMonth = s.calendarMonth ?: cur.calendarMonth,
        calendarKind = s.calendarKind?.takeIf { it in CALENDAR_KINDS } ?: cur.calendarKind,
        calendarMonitoredOnly = s.calendarMonitoredOnly ?: cur.calendarMonitoredOnly,
        diskWarnPercent = s.diskWarnPercent?.takeIf { it in DISK_WARN } ?: cur.diskWarnPercent,
    )

    /** True when a server of the same service at the same address is already set up in [here]. */
    fun isHere(s: BackupServer, here: List<Server>): Boolean =
        here.any { it.type.id == s.type && it.url.equals(s.url, ignoreCase = true) }

    /** The servers in the backup that aren't set up on this phone. */
    fun missing(servers: List<BackupServer>, here: List<Server>): List<BackupServer> = servers.filterNot { isHere(it, here) }
}
