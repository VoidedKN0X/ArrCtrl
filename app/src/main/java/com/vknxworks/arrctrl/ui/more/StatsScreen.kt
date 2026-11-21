package com.vknxworks.arrctrl.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.dbl
import com.vknxworks.arrctrl.data.api.long
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.servers.icon
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.text.NumberFormat

/** One number on the statistics page: a label (string resource) and its value as text. */
data class Stat(val label: Int, val value: String)

/** The numbers of one server, or why they couldn't be read. [bytes] is what its library takes on disk (0 when it has none). */
data class ServerStats(val server: Server, val stats: List<Stat>, val note: String?, val bytes: Long, val failure: TestResult?)

private fun n(value: Number): String = NumberFormat.getIntegerInstance().format(value)

/** Reads the numbers for [s] from what the server already reports about its library; nothing is stored. */
private suspend fun statsFor(c: AppContainer, s: Server): ServerStats? = try {
    when (s.type) {
        ServiceType.RADARR -> {
            val movies = c.radarr(s).movies()
            val bytes = movies.sumOf { it.sizeOnDisk }
            ServerStats(
                s,
                listOf(
                    Stat(R.string.stats_movies, n(movies.size)),
                    Stat(R.string.stats_downloaded, n(movies.count { it.hasFile })),
                    Stat(R.string.stats_missing, n(movies.count { it.monitored && !it.hasFile && it.isAvailable })),
                    Stat(R.string.stats_on_disk, Format.bytes(bytes)),
                ),
                c.context.getString(R.string.stats_monitored_note, n(movies.count { it.monitored }), n(movies.count { !it.monitored })),
                bytes,
                null,
            )
        }
        ServiceType.SONARR -> {
            val series = c.sonarr(s).allSeries()
            val bytes = series.sumOf { it.sizeOnDisk }
            val files = series.sumOf { it.episodeFileCount }
            // Episodes Sonarr expects (aired and monitored) that have no file yet.
            val missing = series.sumOf { (it.episodeCount - it.episodeFileCount).coerceAtLeast(0) }
            ServerStats(
                s,
                listOf(
                    Stat(R.string.stats_series, n(series.size)),
                    Stat(R.string.stats_episodes, n(files)),
                    Stat(R.string.stats_missing, n(missing)),
                    Stat(R.string.stats_on_disk, Format.bytes(bytes)),
                ),
                c.context.getString(R.string.stats_series_note, n(series.count { !it.ended }), n(series.count { it.ended }), n(series.count { it.monitored })),
                bytes,
                null,
            )
        }
        ServiceType.LIDARR -> {
            val artists = c.lidarr(s).artists()
            val bytes = artists.sumOf { it.sizeOnDisk }
            ServerStats(
                s,
                listOf(
                    Stat(R.string.stats_artists, n(artists.size)),
                    Stat(R.string.stats_albums, n(artists.sumOf { it.albumCount })),
                    Stat(R.string.stats_tracks, n(artists.sumOf { it.trackFileCount })),
                    Stat(R.string.stats_on_disk, Format.bytes(bytes)),
                ),
                c.context.getString(R.string.stats_artists_note, n(artists.count { it.monitored }), n(artists.sumOf { (it.trackCount - it.trackFileCount).coerceAtLeast(0) })),
                bytes,
                null,
            )
        }
        ServiceType.QBITTORRENT -> {
            val api = c.qbit(s)
            val state = QbitApi.SyncState()
            api.sync(state)
            val torrents = api.torrents(state)
            val server = JsonObject(state.serverState)
            ServerStats(
                s,
                listOf(
                    Stat(R.string.stats_torrents, n(torrents.size)),
                    Stat(R.string.stats_torrents_size, Format.bytes(torrents.sumOf { it.size })),
                    Stat(R.string.stats_all_time_down, Format.bytes(server.long("alltime_dl") ?: 0)),
                    Stat(R.string.stats_all_time_up, Format.bytes(server.long("alltime_ul") ?: 0)),
                ),
                c.context.getString(R.string.stats_ratio_note, Format.ratio(server.dbl("global_ratio")), n(torrents.count { it.progress >= 1.0 })),
                0,
                null,
            )
        }
        ServiceType.PROWLARR -> {
            val stats = c.prowlarr(s).stats()
            ServerStats(
                s,
                listOf(
                    Stat(R.string.stats_indexers, n(stats.size)),
                    Stat(R.string.stats_queries, n(stats.sumOf { it.queries + it.rssQueries })),
                    Stat(R.string.stats_grabs, n(stats.sumOf { it.grabs })),
                    Stat(R.string.stats_failed_queries, n(stats.sumOf { it.failedQueries })),
                ),
                stats.maxByOrNull { it.grabs }?.takeIf { it.grabs > 0 }?.let { c.context.getString(R.string.stats_top_indexer, it.name, n(it.grabs)) },
                0,
                null,
            )
        }
        // Bazarr, autobrr and Cleanuparr have their own pages with their own numbers.
        else -> null
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ServerStats(s, emptyList(), null, 0, e.toTestResult())
}

private suspend fun allStats(c: AppContainer, servers: List<Server>): List<ServerStats> = coroutineScope {
    servers.map { s -> async { statsFor(c, s) } }.awaitAll().filterNotNull()
}

/** Statistics: the size and state of every library and download client, added up from what the servers report. */
@Composable
fun StatsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    var results by remember { mutableStateOf<List<ServerStats>?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(servers) { results = allStats(c, servers) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.stats_title)) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; results = allStats(c, servers); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val list = results
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> MessageView(Icons.Outlined.Info, stringResource(R.string.stats_none), stringResource(R.string.stats_none_body))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    val libraries = list.filter { it.failure == null && it.server.type.isMediaArr }
                    if (libraries.size > 1) {
                        item(key = "total") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(stringResource(R.string.stats_total_on_disk), Format.bytes(libraries.sumOf { it.bytes }), Modifier.weight(1f))
                                StatTile(stringResource(R.string.stats_libraries), n(libraries.size), Modifier.weight(1f))
                            }
                        }
                    }
                    items(list, key = { it.server.id }) { r ->
                        if (r.failure != null) {
                            ServerErrorCard(r.server, r.failure)
                        } else {
                            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(r.server.type.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text(r.server.name, style = MaterialTheme.typography.titleMedium)
                                }
                                // Two tiles a row, so the numbers stay readable on a narrow phone.
                                r.stats.chunked(2).forEach { pair ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { st -> StatTile(stringResource(st.label), st.value, Modifier.weight(1f)) }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                                r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}
