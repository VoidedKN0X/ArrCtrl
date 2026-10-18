package com.vknxworks.arrctrl.ui.movies

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.data.api.Movie
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.Tag
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/** The one badge a movie shows in the library. Unmonitored is shown separately, on top. */
enum class MovieBadge { DOWNLOADING, DOWNLOADED, MISSING, IN_CINEMAS, ANNOUNCED }

fun Movie.badge(downloading: Boolean): MovieBadge = when {
    downloading -> MovieBadge.DOWNLOADING
    hasFile -> MovieBadge.DOWNLOADED
    isAvailable -> MovieBadge.MISSING
    status == "inCinemas" -> MovieBadge.IN_CINEMAS
    else -> MovieBadge.ANNOUNCED
}

enum class MovieSort { TITLE, YEAR, ADDED, RELEASE, SIZE, RATING }

enum class MovieFilter { ALL, MONITORED, UNMONITORED, MISSING, DOWNLOADED, DOWNLOADING }

/** Library state for the Movies tab: one Radarr server at a time (pick another with the chips). */
class MoviesModel(private val c: AppContainer) : ViewModel() {
    val radarrs: List<Server> get() = c.servers.servers.value.filter { it.type == ServiceType.RADARR }

    var serverId by mutableStateOf<String?>(null)
    val server: Server? get() = radarrs.firstOrNull { it.id == serverId } ?: radarrs.firstOrNull()

    var movies by mutableStateOf<List<Movie>>(emptyList()); private set
    var downloading by mutableStateOf<Set<Int>>(emptySet()); private set
    var profiles by mutableStateOf<List<Profile>>(emptyList()); private set
    var tags by mutableStateOf<List<Tag>>(emptyList()); private set
    var error by mutableStateOf<TestResult?>(null); private set
    var loaded by mutableStateOf(false); private set
    private var loadedFor: String? = null

    var query by mutableStateOf("")
    var filter by mutableStateOf(MovieFilter.ALL)
    var selection by mutableStateOf<Set<Int>>(emptySet())

    /** Loads the library; profiles and tags too (they rarely change, but are cheap). */
    suspend fun load() = coroutineScope {
        val s = server ?: return@coroutineScope
        if (loadedFor != s.id) {
            movies = emptyList(); loaded = false; error = null; selection = emptySet()
        }
        val api = c.radarr(s)
        val m = async { runCatching { api.movies() } }
        val q = async { runCatching { api.queuedMovieIds() } }
        val p = async { runCatching { api.profiles() } }
        val t = async { runCatching { api.tags() } }
        val mr = m.await()
        mr.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        mr.onSuccess { movies = it; error = null }.onFailure { error = it.toTestResult() }
        q.await().onSuccess { downloading = it }
        p.await().onSuccess { profiles = it }
        t.await().onSuccess { tags = it }
        loadedFor = s.id
        loaded = true
    }

    fun profileName(id: Int): String? = profiles.firstOrNull { it.id == id }?.name

    fun visible(sort: MovieSort, descending: Boolean): List<Movie> {
        val q = query.trim()
        val list = movies.filter { m ->
            (q.isEmpty() || m.title.contains(q, ignoreCase = true) || m.year?.toString() == q) &&
                when (filter) {
                    MovieFilter.ALL -> true
                    MovieFilter.MONITORED -> m.monitored
                    MovieFilter.UNMONITORED -> !m.monitored
                    MovieFilter.MISSING -> m.monitored && !m.hasFile && m.isAvailable
                    MovieFilter.DOWNLOADED -> m.hasFile
                    MovieFilter.DOWNLOADING -> m.id in downloading
                }
        }
        val byTitle = compareBy<Movie> { it.sortTitle }
        val cmp: Comparator<Movie> = when (sort) {
            MovieSort.TITLE -> byTitle
            MovieSort.YEAR -> compareBy<Movie> { it.year ?: 0 }.then(byTitle)
            MovieSort.ADDED -> compareBy<Movie> { it.added }.then(byTitle)
            MovieSort.RELEASE -> compareBy<Movie> { it.releaseDate }.then(byTitle)
            MovieSort.SIZE -> compareBy<Movie> { it.sizeOnDisk }.then(byTitle)
            MovieSort.RATING -> compareBy<Movie> { it.imdbRating ?: it.tmdbRating ?: 0.0 }.then(byTitle)
        }
        return if (descending) list.sortedWith(cmp.reversed()) else list.sortedWith(cmp)
    }
}
