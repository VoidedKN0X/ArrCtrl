package com.vknxworks.arrctrl.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hand-offs to the other Ctrl apps. Each tries the app's deep link first and falls back to just opening
 * the app, so it works before and after the app supports the link.
 */
object CtrlApps {
    const val JELLYCTRL = "com.vknxworks.jellyctrl"
    const val SEERRCTRL = "com.vknxworks.seerrctrl"

    fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun jelly(type: String, vararg params: Pair<String, Any?>): Uri =
        Uri.Builder().scheme("jellyctrl").authority("item").appendQueryParameter("type", type).apply {
            params.forEach { (k, v) -> if (v != null && v.toString().isNotBlank()) appendQueryParameter(k, v.toString()) }
        }.build()

    /**
     * jellyctrl://item?type=movie&tmdb=…&imdb=…&title=…&year=…. Every id ArrCtrl knows plus the title and year, so
     * JellyCtrl can find the movie even when Jellyfin has it under a different provider.
     */
    fun jellyMovie(tmdbId: Int?, imdbId: String?, title: String, year: Int?): Uri =
        jelly("movie", "tmdb" to tmdbId, "imdb" to imdbId, "title" to title, "year" to year)

    /** jellyctrl://item?type=episode&tvdb=…&tmdb=…&imdb=…&title=…&year=…&season=…&episode=… (ids and title are the series'). */
    fun jellyEpisode(tvdbId: Int?, tmdbId: Int?, imdbId: String?, seriesTitle: String, year: Int?, season: Int, episode: Int): Uri =
        jelly("episode", "tvdb" to tvdbId, "tmdb" to tmdbId, "imdb" to imdbId, "title" to seriesTitle, "year" to year, "season" to season, "episode" to episode)

    /** seerrctrl://movie/{tmdbId} and seerrctrl://tv/{tmdbId}. */
    fun seerrMovie(tmdbId: Int): Uri = Uri.parse("seerrctrl://movie/$tmdbId")
    fun seerrTv(tmdbId: Int): Uri = Uri.parse("seerrctrl://tv/$tmdbId")

    fun open(context: Context, pkg: String, link: Uri?): Boolean {
        if (link != null) {
            val view = Intent(Intent.ACTION_VIEW, link).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (view.resolveActivity(context.packageManager) != null) {
                return runCatching { context.startActivity(view) }.isSuccess
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(launch) }.isSuccess
    }
}
