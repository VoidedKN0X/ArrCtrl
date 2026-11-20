package com.vknxworks.arrctrl.data.net

/**
 * Remembers when a library was last loaded completely, so coming straight back to its tab doesn't have to
 * download all of it again. A load only counts as fresh for a short while, for exactly the same saved
 * server, and only as long as nothing has been changed on that server from this app since the load began.
 * Pull to refresh and the reload after an action never ask this: they always load.
 *
 * [now] is a clock that keeps counting while the phone sleeps (milliseconds); [writes] is the number of
 * changing requests sent to a server so far ([Http.writeMark]). Used from the main thread only.
 */
class Freshness(
    private val now: () -> Long,
    private val writes: (serverId: String) -> Long,
    private val windowMs: Long = WINDOW_MS,
) {
    /** One load: what was loaded, when it began and how many changes had been sent to the server by then. */
    class Load internal constructor(internal val serverId: String, internal val what: Any, internal val at: Long, internal val writes: Long)

    private var last: Load? = null

    /** A load begins. Until it has completed, nothing counts as fresh. */
    fun begin(serverId: String, what: Any): Load {
        last = null
        return Load(serverId, what, now(), writes(serverId))
    }

    /** That load brought in everything it asked for. */
    fun complete(load: Load) {
        last = load
    }

    /** True when the last complete load was of exactly [what], began less than the window ago and nothing was changed since. */
    fun isFresh(serverId: String, what: Any): Boolean {
        val l = last ?: return false
        val age = now() - l.at
        return l.serverId == serverId && l.what == what && age >= 0 && age < windowMs && writes(serverId) == l.writes
    }

    companion object {
        /** How long a loaded library is shown again without asking the server. */
        const val WINDOW_MS = 30_000L
    }
}
