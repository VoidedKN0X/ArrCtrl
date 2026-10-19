package com.vknxworks.arrctrl.data.server

import androidx.annotation.StringRes
import com.vknxworks.arrctrl.R

/** How a service authenticates. */
enum class AuthKind {
    /** One header with the API key on every request. */
    API_KEY,

    /** qBittorrent: an API key (qBittorrent 5.2+, sent as "Authorization: Bearer"), or a cookie session from username and password. */
    QBITTORRENT,
}

/**
 * The seven services ArrCtrl talks to. [apiBase] is appended to the server address
 * (which may itself carry a reverse-proxy path, e.g. https://example.com/radarr).
 */
enum class ServiceType(
    val id: String,
    @StringRes val label: Int,
    @StringRes val role: Int,
    @StringRes val keyHelp: Int,
    val apiBase: String,
    val authHeader: String,
    val auth: AuthKind,
    val defaultPort: Int,
) {
    RADARR("radarr", R.string.service_radarr, R.string.service_radarr_role, R.string.key_help_arr, "/api/v3", "X-Api-Key", AuthKind.API_KEY, 7878),
    SONARR("sonarr", R.string.service_sonarr, R.string.service_sonarr_role, R.string.key_help_arr, "/api/v3", "X-Api-Key", AuthKind.API_KEY, 8989),
    QBITTORRENT("qbittorrent", R.string.service_qbittorrent, R.string.service_qbittorrent_role, R.string.key_help_qbittorrent, "/api/v2", "Authorization", AuthKind.QBITTORRENT, 8080),
    PROWLARR("prowlarr", R.string.service_prowlarr, R.string.service_prowlarr_role, R.string.key_help_arr, "/api/v1", "X-Api-Key", AuthKind.API_KEY, 9696),
    BAZARR("bazarr", R.string.service_bazarr, R.string.service_bazarr_role, R.string.key_help_bazarr, "/api", "X-API-KEY", AuthKind.API_KEY, 6767),
    AUTOBRR("autobrr", R.string.service_autobrr, R.string.service_autobrr_role, R.string.key_help_autobrr, "/api", "X-API-Token", AuthKind.API_KEY, 7474),
    CLEANUPARR("cleanuparr", R.string.service_cleanuparr, R.string.service_cleanuparr_role, R.string.key_help_cleanuparr, "/api", "X-Api-Key", AuthKind.API_KEY, 11011),
    ;

    /** The header value carrying [key]: qBittorrent wants "Bearer <key>", the others the bare key. */
    fun authValue(key: String): String = if (auth == AuthKind.QBITTORRENT) "Bearer $key" else key

    companion object {
        fun of(id: String?): ServiceType? =
            id?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.id == v || it.name.lowercase() == v } }
    }
}
