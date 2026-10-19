package com.vknxworks.arrctrl.ui.series

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Series
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.activity.CheckRow
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.ChoiceSheet
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.SelectionAction
import com.vknxworks.arrctrl.ui.components.SelectionBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.movies.Poster
import com.vknxworks.arrctrl.ui.movies.Scrim
import com.vknxworks.arrctrl.ui.nav.AddSeriesRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.SeriesDetailRoute
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun SeriesScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val sonarrs = servers.filter { it.type == ServiceType.SONARR }

    if (sonarrs.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_series))
            MessageView(
                Icons.Outlined.Tv,
                stringResource(R.string.empty_series_title),
                stringResource(R.string.empty_series_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
                actionLabel = stringResource(R.string.add_service, stringResource(R.string.service_sonarr)),
                onAction = { nav.navigate(ServerEditRoute(type = ServiceType.SONARR.id)) },
            )
        }
        return
    }

    val model: SeriesModel = viewModel { SeriesModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val sort = runCatching { SeriesSort.valueOf(settings.seriesSort) }.getOrDefault(SeriesSort.TITLE)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var searching by remember { mutableStateOf(model.query.isNotEmpty()) }
    var sortSheet by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<SelectionAction?>(null) }
    val server = model.server ?: return

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(server.id, servers) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { model.load() } }
    }

    val visible = model.visible(sort, settings.seriesSortDescending)
    val selecting = model.selection.isNotEmpty()
    fun toggle(id: Int) { model.selection = if (id in model.selection) model.selection - id else model.selection + id }
    fun open(s: Series) { if (selecting) toggle(s.id) else nav.navigate(SeriesDetailRoute(s.server.id, s.id)) }
    fun longPress(s: Series) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggle(s.id) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selecting) {
            SelectionBar(
                count = model.selection.size,
                onClose = { model.selection = emptySet() },
                onSelectAll = { model.selection = visible.map { it.id }.toSet() },
                onAction = { bulk = it },
            )
        } else {
            TabTitle(stringResource(R.string.tab_series)) {
                IconButton(onClick = { searching = !searching; if (!searching) model.query = "" }) {
                    Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, stringResource(R.string.search))
                }
                IconButton(onClick = { sortSheet = true }) { Icon(Icons.Outlined.Sort, stringResource(R.string.sort)) }
                IconButton(onClick = { scope.launch { c.settings.update { it.copy(seriesGrid = !it.seriesGrid) } } }) {
                    Icon(
                        if (settings.seriesGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        stringResource(if (settings.seriesGrid) R.string.view_list else R.string.view_grid),
                    )
                }
                IconButton(onClick = { nav.navigate(AddSeriesRoute(server.id)) }) { Icon(Icons.Outlined.Add, stringResource(R.string.series_add)) }
            }
        }

        if (sonarrs.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sonarrs.forEach { s -> FilterChip(selected = s.id == server.id, onClick = { model.serverId = s.id }, label = { Text(s.name) }) }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.query = it },
                placeholder = { Text(stringResource(R.string.series_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (model.query.isNotEmpty()) IconButton(onClick = { model.query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeriesFilter.entries.forEach { f ->
                FilterChip(selected = f == model.filter, onClick = { model.filter = f }, label = { Text(stringResource(f.label)) })
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; runCatching { model.load() }; refreshing = false } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val bottom = 24.dp + LocalBottomClearance.current
            when {
                model.error != null && model.series.isEmpty() -> Column { ServerErrorCard(server, model.error!!) }
                !model.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                visible.isEmpty() -> MessageView(
                    Icons.Outlined.Tv,
                    stringResource(if (model.series.isEmpty()) R.string.series_none else R.string.series_no_match),
                    if (model.series.isEmpty()) stringResource(R.string.movies_none_body) else null,
                    Modifier.padding(bottom = bottom),
                )
                settings.seriesGrid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    model.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { ServerErrorCard(server, e) } }
                    items(visible, key = { it.key }) { s ->
                        GridTile(s, downloading = s.id in model.downloading, selected = s.id in model.selection, onClick = { open(s) }, onLongClick = { longPress(s) })
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottom)) {
                    model.error?.let { e -> item { ServerErrorCard(server, e) } }
                    items(visible, key = { it.key }) { s ->
                        ListRow(s, model, selected = s.id in model.selection, onClick = { open(s) }, onLongClick = { longPress(s) })
                    }
                }
            }
        }
    }

    if (sortSheet) {
        ChoiceSheet(
            stringResource(R.string.sort),
            SeriesSort.entries.flatMap { s ->
                listOf(
                    (s.name to false) to stringResource(s.label) + " " + stringResource(R.string.sort_ascending),
                    (s.name to true) to stringResource(s.label) + " " + stringResource(R.string.sort_descending),
                )
            },
            selected = settings.seriesSort to settings.seriesSortDescending,
            onDismiss = { sortSheet = false },
        ) { (name, desc) ->
            sortSheet = false
            scope.launch { c.settings.update { it.copy(seriesSort = name, seriesSortDescending = desc) } }
        }
    }

    bulk?.let { action ->
        SelectionActionDialog(action, model, onDismiss = { bulk = null }) { msg ->
            bulk = null
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            model.selection = emptySet()
            scope.launch { runCatching { model.load() } }
        }
    }
}

private val SeriesFilter.label: Int
    get() = when (this) {
        SeriesFilter.ALL -> R.string.filter_all
        SeriesFilter.MONITORED -> R.string.filter_monitored
        SeriesFilter.UNMONITORED -> R.string.filter_unmonitored
        SeriesFilter.CONTINUING -> R.string.filter_continuing
        SeriesFilter.ENDED -> R.string.filter_ended
        SeriesFilter.MISSING -> R.string.filter_missing
        SeriesFilter.DOWNLOADING -> R.string.filter_downloading
    }

private val SeriesSort.label: Int
    get() = when (this) {
        SeriesSort.TITLE -> R.string.sort_title
        SeriesSort.YEAR -> R.string.sort_year
        SeriesSort.ADDED -> R.string.sort_added
        SeriesSort.NEXT_AIRING -> R.string.sort_next_airing
        SeriesSort.PREVIOUS_AIRING -> R.string.sort_previous_airing
        SeriesSort.SIZE -> R.string.sort_size
        SeriesSort.PROGRESS -> R.string.sort_progress
    }

/** Progress bar colour: done, still missing episodes, or unmonitored. */
@Composable
fun progressColor(s: Series): Color = when {
    !s.monitored -> MaterialTheme.colorScheme.outline
    s.complete -> MaterialTheme.colorScheme.tertiary
    s.ended -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun GridTile(s: Series, downloading: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val api = LocalContext.current.container.sonarr(s.server)
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Poster(api.poster(s.id), s.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (downloading) {
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                    Chip(stringResource(R.string.badge_downloading), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (!s.monitored) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    stringResource(R.string.badge_unmonitored),
                    Modifier.align(Alignment.TopEnd).padding(6.dp).background(Scrim, CircleShape).padding(4.dp).size(16.dp),
                    tint = Color.White,
                )
            }
            if (selected) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.shapes.medium))
                Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.Center).size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        LinearProgressIndicator(
            progress = { s.progress },
            color = progressColor(s),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(s.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${s.episodeFileCount}/${s.episodeCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListRow(s: Series, model: SeriesModel, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val api = LocalContext.current.container.sonarr(s.server)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Poster(api.poster(s.id), "", Modifier.width(54.dp).aspectRatio(2f / 3f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    s.year?.toString(),
                    s.network,
                    pluralStringResource(R.plurals.season_count, s.seasons.count { it.number > 0 }, s.seasons.count { it.number > 0 }),
                    s.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                    model.profileName(s.qualityProfileId),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { s.progress }, color = progressColor(s), modifier = Modifier.weight(1f).height(4.dp))
                Text("${s.episodeFileCount}/${s.episodeCount}", style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.id in model.downloading) Chip(stringResource(R.string.badge_downloading), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                Chip(stringResource(if (s.ended) R.string.series_ended else R.string.series_continuing))
                if (!s.monitored) Chip(stringResource(R.string.badge_unmonitored))
            }
        }
    }
}

@Composable
private fun SelectionActionDialog(action: SelectionAction, model: SeriesModel, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = model.server ?: return
    val ids = model.selection.toList()
    val api = c.sonarr(server)
    val doneText = stringResource(R.string.bulk_done, ids.size)

    fun exec(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { onDone(doneText) }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show(); onDismiss() }
        }
    }

    when (action) {
        SelectionAction.PROFILE -> ChoiceSheet(
            stringResource(R.string.bulk_profile),
            model.profiles.map { it.id to it.name },
            selected = null,
            onDismiss = onDismiss,
        ) { id -> exec { api.editor(ids, qualityProfileId = id) } }
        SelectionAction.DELETE -> {
            var files by remember { mutableStateOf(false) }
            var exclude by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(pluralStringResource(R.plurals.series_delete_title, ids.size, ids.size)) },
                text = {
                    Column {
                        CheckRow(stringResource(R.string.series_delete_files), files) { files = it }
                        CheckRow(stringResource(R.string.movie_add_exclusion), exclude) { exclude = it }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { exec { api.deleteMany(ids, files, exclude) } }) {
                        Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        else -> {
            val title = stringResource(
                when (action) {
                    SelectionAction.MONITOR -> R.string.bulk_monitor
                    SelectionAction.UNMONITOR -> R.string.bulk_unmonitor
                    else -> R.string.bulk_search
                },
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = { Text(pluralStringResource(R.plurals.bulk_confirm_series, ids.size, ids.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        exec {
                            when (action) {
                                SelectionAction.MONITOR -> api.editor(ids, monitored = true)
                                SelectionAction.UNMONITOR -> api.editor(ids, monitored = false)
                                else -> api.searchMany(ids)
                            }
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
