package com.vknxworks.arrctrl.ui.wanted

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.CtrlAlertDialog
import com.vknxworks.arrctrl.ui.components.CtrlFilterChip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.ctrlSegmentedColors
import com.vknxworks.arrctrl.ui.components.topDivider
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieRoute
import com.vknxworks.arrctrl.ui.nav.SeasonRoute
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.servers.failureText
import com.vknxworks.arrctrl.ui.servers.icon
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.CtrlType
import com.vknxworks.arrctrl.ui.theme.Gutter
import com.vknxworks.arrctrl.ui.series.date
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** One movie, episode or album on a Wanted list. [route] is where tapping it goes. */
data class WantedItem(
    val id: Int,
    val title: String,
    val subtitle: String?,
    /** Cutoff unmet: the quality the current file has. */
    val quality: String?,
    val dateMs: Long?,
    val route: Any?,
)

/** Which services have a Wanted list. */
val WANTED_TYPES = setOf(ServiceType.RADARR, ServiceType.SONARR)

private const val PAGE = 50

/** One page from [s]: missing or (with [cutoff]) cutoff unmet, plus the total. */
suspend fun loadWanted(c: AppContainer, s: Server, cutoff: Boolean, page: Int): Pair<List<WantedItem>, Int> = when (s.type) {
    ServiceType.RADARR -> c.radarr(s).wanted(cutoff, page, PAGE).let { (list, total) ->
        list.map { m ->
            WantedItem(
                id = m.id,
                title = m.title,
                subtitle = m.year?.toString(),
                quality = if (cutoff) m.file?.quality else null,
                dateMs = m.releaseDate?.toEpochMilli(),
                route = MovieRoute(s.id, m.id),
            )
        } to total
    }
    ServiceType.SONARR -> c.sonarr(s).wanted(cutoff, page, PAGE).let { (list, total) ->
        list.map { w ->
            val e = w.episode
            WantedItem(
                id = e.id,
                title = w.series?.title ?: "#${e.seriesId}",
                subtitle = listOfNotNull(e.code.ifBlank { null }, e.title).joinToString(" · ").ifBlank { null },
                quality = w.currentQuality,
                dateMs = e.airDate?.toEpochMilli(),
                route = SeasonRoute(s.id, e.seriesId, e.season),
            )
        } to total
    }
    else -> emptyList<WantedItem>() to 0
}

/** Searches the chosen items (MoviesSearch / EpisodeSearch). */
suspend fun searchWanted(c: AppContainer, s: Server, ids: List<Int>) {
    when (s.type) {
        ServiceType.RADARR -> c.radarr(s).search(ids)
        ServiceType.SONARR -> c.sonarr(s).searchEpisodes(ids)
        else -> Unit
    }
}

/** Searches everything on the list (MissingMoviesSearch, CutoffUnmetMoviesSearch, MissingEpisodeSearch, …). */
suspend fun searchAllWanted(c: AppContainer, s: Server, cutoff: Boolean) {
    when (s.type) {
        ServiceType.RADARR -> if (cutoff) c.radarr(s).searchAllCutoffUnmet() else c.radarr(s).searchAllMissing()
        ServiceType.SONARR -> if (cutoff) c.sonarr(s).searchAllCutoffUnmet() else c.sonarr(s).searchAllMissing()
        else -> Unit
    }
}

/**
 * Wanted: monitored movies and episodes (and albums, with Lidarr) that are missing, or whose file is below the
 * quality profile's cutoff. One server at a time, 50 per page. Tap to open, long-press to select, then search the
 * selection; or search the whole list from the top bar.
 */
@Composable
fun WantedScreen(initialServerId: String? = null) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val all by c.servers.servers.collectAsStateWithLifecycle()
    val servers = all.filter { it.type in WANTED_TYPES }
    var chosen by rememberSaveable { mutableStateOf(initialServerId) }
    val server = servers.firstOrNull { it.id == chosen } ?: servers.firstOrNull()
    var cutoff by rememberSaveable { mutableStateOf(false) }

    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.wanted))
            MessageView(
                Icons.Outlined.CloudOff,
                stringResource(R.string.wanted_no_server),
                actionLabel = stringResource(R.string.add_service, stringResource(R.string.service_radarr)),
                onAction = { nav.navigate(ServerEditRoute(type = ServiceType.RADARR.id)) },
            )
        }
        return
    }

    var rows by remember(server.id, cutoff) { mutableStateOf<List<WantedItem>>(emptyList()) }
    var total by remember(server.id, cutoff) { mutableIntStateOf(0) }
    var page by remember(server.id, cutoff) { mutableIntStateOf(0) }
    var loading by remember(server.id, cutoff) { mutableStateOf(false) }
    var error by remember(server.id, cutoff) { mutableStateOf<TestResult?>(null) }
    var selection by remember(server.id, cutoff) { mutableStateOf<Set<Int>>(emptySet()) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            val next = if (reset) 1 else page + 1
            val (list, t) = loadWanted(c, server, cutoff, next)
            rows = if (reset) list else (rows + list).distinctBy { it.id }
            total = t
            page = next
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(server.id, cutoff) { load(reset = true) }

    val searchStarted = stringResource(R.string.movie_search_started)
    fun search(ids: List<Int>) {
        scope.launch {
            runCatching { searchWanted(c, server, ids) }
                .onSuccess { Toast.makeText(context, searchStarted, Toast.LENGTH_SHORT).show(); selection = emptySet() }
                .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear)) }
                Text(
                    stringResource(R.string.selection_count, selection.size),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = { selection = rows.map { it.id }.toSet() }) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.selection_all)) }
                IconButton(onClick = { search(selection.toList()) }) { Icon(Icons.Outlined.Search, stringResource(R.string.wanted_search_selected)) }
            }
        } else {
            ScreenTopBar(stringResource(R.string.wanted)) {
                IconButton(onClick = { confirmAll = true }, enabled = total > 0) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.wanted_search_all))
                }
            }
        }
        if (servers.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s ->
                    CtrlFilterChip(
                        selected = s.id == server.id,
                        onClick = { chosen = s.id },
                        label = { Text(s.name) },
                        leadingIcon = { Icon(s.type.icon, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            listOf(false, true).forEachIndexed { i, cut ->
                val label = stringResource(if (cut) R.string.wanted_cutoff else R.string.wanted_missing)
                SegmentedButton(
                    selected = cut == cutoff,
                    onClick = { cutoff = cut },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = ctrlSegmentedColors(),
                    icon = {},
                ) {
                    Text(if (cut == cutoff && page > 0 && total > 0) stringResource(R.string.label_count, label, total) else label, maxLines = 1)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(reset = true); refreshing = false } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                error?.let { e -> item(key = "error") { ServerErrorCard(server, e) } }
                if (page == 0 && loading) {
                    item(key = "loading") { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (page > 0 && rows.isEmpty() && error == null) {
                    item(key = "empty") {
                        MessageView(
                            Icons.Outlined.TaskAlt,
                            stringResource(if (cutoff) R.string.wanted_cutoff_empty else R.string.wanted_missing_empty),
                            stringResource(R.string.wanted_empty_body),
                        )
                    }
                }
                items(rows, key = { it.id }) { w ->
                    val selected = w.id in selection
                    WantedRow(
                        w,
                        server,
                        selected,
                        onClick = {
                            if (selection.isNotEmpty()) selection = if (selected) selection - w.id else selection + w.id
                            else w.route?.let { nav.navigate(it) }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selection = if (selected) selection - w.id else selection + w.id
                        },
                        onSearch = { search(listOf(w.id)) },
                    )
                }
                if (page > 0 && rows.size < total) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else OutlinedButton(onClick = { scope.launch { load(reset = false) } }, shape = CtrlShapes.Button) {
                                Text(stringResource(R.string.history_load_more))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        CtrlAlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.wanted_search_all)) },
            text = {
                Text(
                    pluralStringResource(
                        if (cutoff) R.plurals.wanted_search_all_cutoff else R.plurals.wanted_search_all_missing,
                        total,
                        total,
                        server.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    scope.launch {
                        runCatching { searchAllWanted(c, server, cutoff) }
                            .onSuccess { Toast.makeText(context, searchStarted, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.search)) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WantedRow(w: WantedItem, server: Server, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter)
            .topDivider()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(server.type.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(w.subtitle, w.dateMs?.let { date(it) }).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = CtrlType.Mono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            w.quality?.let { Chip(it) }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.movie_search)) }
    }
}
