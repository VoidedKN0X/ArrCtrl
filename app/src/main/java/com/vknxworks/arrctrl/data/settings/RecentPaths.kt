package com.vknxworks.arrctrl.data.settings

import android.content.Context

/**
 * Folders you recently moved torrents to (qBittorrent → Set location), newest first, per qBittorrent server.
 * Kept on the phone only, and forgotten with "Forgot PIN?" or when the server is removed.
 */
object RecentPaths {
    private const val PREFS = "recent_paths"
    private const val MAX = 8

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, serverId: String): List<String> =
        prefs(context).getString(serverId, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(context: Context, serverId: String, path: String) {
        val p = path.trim()
        if (p.isEmpty()) return
        val list = (listOf(p) + get(context, serverId).filter { it != p }).take(MAX)
        prefs(context).edit().putString(serverId, list.joinToString("\n")).apply()
    }

    fun remove(context: Context, serverId: String, path: String) {
        val list = get(context, serverId).filter { it != path }
        prefs(context).edit().putString(serverId, list.joinToString("\n")).apply()
    }

    /** Drops the lists of servers that no longer exist; [keep] null drops everything. */
    fun prune(context: Context, keep: Set<String>?) {
        val p = prefs(context)
        p.edit().apply { p.all.keys.filter { keep == null || it !in keep }.forEach { remove(it) } }.apply()
    }
}
