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

    /** jellyctrl://item?tmdb=…&type=movie (or tvdb, season and episode for episodes). */
    fun jellyMovie(tmdbId: Int): Uri = Uri.parse("jellyctrl://item?type=movie&tmdb=$tmdbId")

    fun jellyEpisode(tvdbId: Int, season: Int, episode: Int): Uri =
        Uri.parse("jellyctrl://item?type=episode&tvdb=$tvdbId&season=$season&episode=$episode")

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
