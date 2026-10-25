package com.vknxworks.arrctrl.ui.links

import android.net.Uri

/**
 * Inbound arrctrl:// links from other apps (JellyCtrl, SeerrCtrl):
 *
 * - arrctrl://movie?tmdb=<id> opens the movie in Radarr, or the Add movie screen looked up by that TMDB id.
 * - arrctrl://series?tvdb=<id>&tmdb=<id> opens the series in Sonarr, or the Add series screen looked up by the
 *   TVDB id (or the TMDB id when there's no TVDB id). Either id is enough.
 *
 * Everything else is ignored: other hosts, paths, user info, ports, fragments, unknown or repeated parameters,
 * and ids that aren't plain positive numbers. A link never adds, searches or changes anything by itself; it only
 * opens a screen, after the app lock.
 */
object DeepLink {
    const val SCHEME = "arrctrl"
    private val ID = Regex("^[1-9][0-9]{0,9}$")

    /** The "open" value for a valid link ("link/movie/<tmdb>" or "link/series/<tvdb>/<tmdb>"), or null. */
    fun parse(uri: Uri?): String? {
        uri ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (uri.isOpaque) return null
        if (uri.userInfo != null || uri.port != -1 || uri.fragment != null) return null
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
        val names = runCatching { uri.queryParameterNames }.getOrNull() ?: return null
        /** The id, 0 when given empty (treated as missing), null when invalid or repeated. */
        fun id(name: String): Int? {
            val values = uri.getQueryParameters(name)
            if (values.size != 1) return null
            if (values[0].isEmpty()) return 0
            return values[0].takeIf { ID.matches(it) }?.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        }
        return when (uri.host?.lowercase()) {
            "movie" -> {
                if (names != setOf("tmdb")) return null
                id("tmdb")?.takeIf { it > 0 }?.let { "link/movie/$it" }
            }
            "series" -> {
                if (names.isEmpty() || !setOf("tvdb", "tmdb").containsAll(names)) return null
                val tvdb = if ("tvdb" in names) id("tvdb") ?: return null else 0
                val tmdb = if ("tmdb" in names) id("tmdb") ?: return null else 0
                if (tvdb == 0 && tmdb == 0) null else "link/series/$tvdb/$tmdb"
            }
            else -> null
        }
    }
}
