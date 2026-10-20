package com.vknxworks.arrctrl.ui.series

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Episode
import com.vknxworks.arrctrl.data.api.EpisodeFileInfo
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Series
import com.vknxworks.arrctrl.data.api.SonarrApi
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.ui.activity.Detail
import com.vknxworks.arrctrl.ui.common.CtrlApps
import com.vknxworks.arrctrl.ui.more.EpisodeSubtitlesButton
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.movies.signed
import com.vknxworks.arrctrl.ui.nav.EpisodeReleasesRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.SeasonReleasesRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun SeasonScreen(serverId: String, seriesId: Int, season: Int) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("")
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.sonarr(server) }

    var series by remember { mutableStateOf<Series?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var queued by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Episode?>(null) }
    var confirmSearch by remember { mutableStateOf(false) }

    suspend fun load() = coroutineScope {
        val s = async { runCatching { api.series(seriesId) } }
        val e = async { runCatching { api.episodes(seriesId) } }
        val q = async { runCatching { api.queued() } }
        s.await().onSuccess { series = it }
        e.await().onSuccess { list -> episodes = list.filter { it.season == season }; error = null }.onFailure { error = it.toTestResult() }
        q.await().onSuccess { queued = it.second }
    }
    LaunchedEffect(reload) { load() }

    fun act(done: Int, block: suspend () -> Unit) {
        val msg = context.getString(done)
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); reload++ }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
        }
    }

    val title = series?.let { "${it.title} · ${seasonName(season)}" } ?: seasonName(season)
    val seasonInfo = series?.seasons?.firstOrNull { it.number == season }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(title) {
                IconButton(onClick = { confirmSearch = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.season_search)) }
                IconButton(onClick = { nav.navigate(SeasonReleasesRoute(server.id, seriesId, season, title)) }) {
                    Icon(Icons.Outlined.ManageSearch, stringResource(R.string.season_interactive))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val s = series
                        if (s != null && seasonInfo != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (seasonInfo.monitored) R.string.season_unmonitor else R.string.season_monitor)) },
                                onClick = {
                                    menu = false
                                    act(if (seasonInfo.monitored) R.string.movie_now_unmonitored else R.string.movie_now_monitored) {
                                        api.setSeasonMonitored(s, season, !seasonInfo.monitored)
                                    }
                                },
                            )
                        }
                        val eps = episodes.orEmpty()
                        if (eps.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.season_monitor_all_episodes)) },
                                onClick = { menu = false; act(R.string.saved) { api.setEpisodesMonitored(eps.map { it.id }, true) } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.season_unmonitor_all_episodes)) },
                                onClick = { menu = false; act(R.string.saved) { api.setEpisodesMonitored(eps.map { it.id }, false) } },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val list = episodes
            when {
                list == null && error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type), actionLabel = stringResource(R.string.retry), onAction = { reload++ })
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.season_no_episodes))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (seasonInfo != null) {
                        item(key = "summary") {
                            Text(
                                listOfNotNull(
                                    "${seasonInfo.episodeFileCount}/${seasonInfo.episodeCount}",
                                    seasonInfo.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                                    stringResource(if (seasonInfo.monitored) R.string.filter_monitored else R.string.filter_unmonitored),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { e ->
                        EpisodeRow(
                            e,
                            downloading = e.id in queued,
                            onToggle = {
                                act(if (e.monitored) R.string.movie_now_unmonitored else R.string.movie_now_monitored) {
                                    api.setEpisodesMonitored(listOf(e.id), !e.monitored)
                                }
                            },
                            onClick = { open = e },
                        )
                    }
                }
            }
        }
    }

    if (confirmSearch) {
        AlertDialog(
            onDismissRequest = { confirmSearch = false },
            title = { Text(stringResource(R.string.season_search_title, seasonName(season))) },
            text = { Text(stringResource(R.string.season_search_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSearch = false; act(R.string.movie_search_started) { api.searchSeason(seriesId, season) } }) {
                    Text(stringResource(R.string.season_search))
                }
            },
            dismissButton = { TextButton(onClick = { confirmSearch = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    open?.let { e ->
        val live = episodes?.firstOrNull { it.id == e.id } ?: e
        EpisodeSheet(
            api,
            series,
            live,
            downloading = live.id in queued,
            onDismiss = { open = null },
            onChanged = { reload++ },
            onInteractive = {
                open = null
                nav.navigate(EpisodeReleasesRoute(server.id, live.id, "${series?.title.orEmpty()} ${live.code}".trim()))
            },
        )
    }
}

@Composable
private fun EpisodeRow(e: Episode, downloading: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = Gutter, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (e.monitored) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                stringResource(if (e.monitored) R.string.season_unmonitor else R.string.season_monitor),
                tint = if (e.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(e.number.toString() + ".", e.title).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    e.airDate?.let { date(it.toEpochMilli()) } ?: stringResource(R.string.episode_tba),
                    e.finaleType?.let { stringResource(R.string.episode_finale) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        EpisodeStatusIcon(e, downloading)
    }
}

@Composable
private fun EpisodeStatusIcon(e: Episode, downloading: Boolean) {
    when {
        downloading -> Icon(Icons.Outlined.CloudDownload, stringResource(R.string.badge_downloading), tint = MaterialTheme.colorScheme.primary)
        e.hasFile -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.badge_downloaded), tint = MaterialTheme.colorScheme.tertiary)
        !e.aired -> Icon(Icons.Outlined.Schedule, stringResource(R.string.episode_not_aired), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        e.monitored -> Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.badge_missing), tint = MaterialTheme.colorScheme.error)
        else -> Icon(Icons.Outlined.RadioButtonUnchecked, stringResource(R.string.badge_missing), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun EpisodeSheet(
    api: SonarrApi,
    series: Series?,
    e: Episode,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onInteractive: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(e.fileId) { mutableStateOf<EpisodeFileInfo?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(e.fileId) { e.fileId?.let { id -> runCatching { api.episodeFile(id) }.onSuccess { file = it } } }

    fun act(done: Int, close: Boolean = false, block: suspend () -> Unit) {
        val msg = context.getString(done)
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); onChanged(); if (close) onDismiss() }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(e.code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.title ?: stringResource(R.string.episode_tba), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(if (e.monitored) R.string.filter_monitored else R.string.filter_unmonitored))
                when {
                    downloading -> Chip(stringResource(R.string.badge_downloading), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    e.hasFile -> Chip(stringResource(R.string.badge_downloaded), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                    e.aired && e.monitored -> Chip(stringResource(R.string.badge_missing), MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                    !e.aired -> Chip(stringResource(R.string.episode_not_aired))
                }
            }
            Text(
                listOfNotNull(
                    e.airDate?.let { date(it.toEpochMilli()) },
                    e.runtime?.let { stringResource(R.string.minutes_short, it) },
                    e.absoluteNumber?.let { stringResource(R.string.episode_absolute, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            e.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            val f = file
            if (f != null) {
                Text(stringResource(R.string.movie_file), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                f.relativePath?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                f.quality?.let { Detail(R.string.detail_quality, if (f.cutoffNotMet) it + " · " + stringResource(R.string.movie_cutoff_unmet) else it) }
                Detail(R.string.detail_size, Format.bytes(f.size))
                listOfNotNull(f.resolution, f.videoCodec, f.videoDynamicRange).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Detail(R.string.detail_video, it) }
                listOfNotNull(f.audioCodec, f.audioChannels?.let { ch -> String.format(java.util.Locale.ROOT, "%.1f", ch) }, f.audioLanguages)
                    .joinToString(" · ").takeIf { it.isNotBlank() }?.let { Detail(R.string.detail_audio, it) }
                f.subtitles?.let { Detail(R.string.detail_subtitles, it) }
                f.releaseGroup?.let { Detail(R.string.detail_release_group, it) }
                f.customFormatScore?.takeIf { it != 0 }?.let { Detail(R.string.detail_cf_score, signed(it)) }
                f.dateAdded?.let { Detail(R.string.detail_added, relative(it.toEpochMilli())) }
            }

            Spacer(Modifier.size(4.dp))
            FilledTonalButton(
                onClick = { act(R.string.movie_search_started) { api.searchEpisodes(listOf(e.id)) } },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Search, null)
                Text(stringResource(R.string.episode_search), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onInteractive, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ManageSearch, null)
                Text(stringResource(R.string.movie_interactive), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { act(if (e.monitored) R.string.movie_now_unmonitored else R.string.movie_now_monitored) { api.setEpisodesMonitored(listOf(e.id), !e.monitored) } },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (e.monitored) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark, null)
                Text(stringResource(if (e.monitored) R.string.season_unmonitor else R.string.season_monitor), Modifier.padding(start = 8.dp))
            }
            if (e.hasFile && series != null) {
                EpisodeSubtitlesButton(series.id, series.tvdbId, series.title, e.id, "${series.title} · ${e.code}", onOpen = onDismiss)
            }
            if (e.hasFile && series != null && CtrlApps.installed(context, CtrlApps.JELLYCTRL)) {
                OutlinedButton(
                    onClick = { CtrlApps.open(context, CtrlApps.JELLYCTRL, CtrlApps.jellyEpisode(series.tvdbId, series.tmdbId, series.imdbId, series.title, series.year, e.season, e.number)) },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Text(stringResource(R.string.play_in_jellyctrl), Modifier.padding(start = 8.dp))
                }
            }
            if (f != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.movie_delete_file), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && file != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.movie_delete_file_title)) },
            text = { Text(file?.relativePath ?: e.code) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = file?.id
                    if (id != null) act(R.string.movie_file_deleted, close = true) { api.deleteEpisodeFile(id) }
                }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
