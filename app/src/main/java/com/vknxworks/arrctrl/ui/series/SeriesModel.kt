package com.vknxworks.arrctrl.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.Series
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

enum class SeriesSort { TITLE, YEAR, ADDED, NEXT_AIRING, PREVIOUS_AIRING, SIZE, PROGRESS }

enum class SeriesFilter { ALL, MONITORED, UNMONITORED, CONTINUING, ENDED, MISSING, DOWNLOADING }

/** Library state for the Series tab: one Sonarr server at a time. */
class SeriesModel(private val c: AppContainer) : ViewModel() {
    val sonarrs: List<Server> get() = c.servers.servers.value.filter { it.type == ServiceType.SONARR }

    var serverId by mutableStateOf<String?>(null)
    val server: Server? get() = sonarrs.firstOrNull { it.id == serverId } ?: sonarrs.firstOrNull()

    var series by mutableStateOf<List<Series>>(emptyList()); private set
    var downloading by mutableStateOf<Set<Int>>(emptySet()); private set
    var profiles by mutableStateOf<List<Profile>>(emptyList()); private set
    var error by mutableStateOf<TestResult?>(null); private set
    var loaded by mutableStateOf(false); private set
    private var loadedFor: String? = null

    var query by mutableStateOf("")
    var filter by mutableStateOf(SeriesFilter.ALL)
    var selection by mutableStateOf<Set<Int>>(emptySet())

    suspend fun load() = coroutineScope {
        val s = server ?: return@coroutineScope
        if (loadedFor != s.id) {
            series = emptyList(); loaded = false; error = null; selection = emptySet()
        }
        val api = c.sonarr(s)
        val all = async { runCatching { api.allSeries() } }
        val q = async { runCatching { api.queued() } }
        val p = async { runCatching { api.profiles() } }
        val r = all.await()
        r.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        r.onSuccess { series = it; error = null }.onFailure { error = it.toTestResult() }
        q.await().onSuccess { downloading = it.first }
        p.await().onSuccess { profiles = it }
        loadedFor = s.id
        loaded = true
    }

    fun profileName(id: Int): String? = profiles.firstOrNull { it.id == id }?.name

    fun visible(sort: SeriesSort, descending: Boolean): List<Series> {
        val q = query.trim()
        val list = series.filter { s ->
            (q.isEmpty() || s.title.contains(q, ignoreCase = true) || s.network?.contains(q, ignoreCase = true) == true) &&
                when (filter) {
                    SeriesFilter.ALL -> true
                    SeriesFilter.MONITORED -> s.monitored
                    SeriesFilter.UNMONITORED -> !s.monitored
                    SeriesFilter.CONTINUING -> !s.ended
                    SeriesFilter.ENDED -> s.ended
                    SeriesFilter.MISSING -> s.monitored && s.episodeFileCount < s.episodeCount
                    SeriesFilter.DOWNLOADING -> s.id in downloading
                }
        }
        val byTitle = compareBy<Series> { it.sortTitle }
        val cmp: Comparator<Series> = when (sort) {
            SeriesSort.TITLE -> byTitle
            SeriesSort.YEAR -> compareBy<Series> { it.year ?: 0 }.then(byTitle)
            SeriesSort.ADDED -> compareBy<Series> { it.added }.then(byTitle)
            // Series without a next episode go last when sorting by next airing.
            SeriesSort.NEXT_AIRING -> compareBy<Series> { it.nextAiring == null }.thenBy { it.nextAiring }.then(byTitle)
            SeriesSort.PREVIOUS_AIRING -> compareBy<Series> { it.previousAiring }.then(byTitle)
            SeriesSort.SIZE -> compareBy<Series> { it.sizeOnDisk }.then(byTitle)
            SeriesSort.PROGRESS -> compareBy<Series> { it.progress }.then(byTitle)
        }
        return if (descending) list.sortedWith(cmp.reversed()) else list.sortedWith(cmp)
    }
}
