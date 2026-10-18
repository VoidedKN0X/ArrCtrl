package com.vknxworks.arrctrl.ui.movies

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.vknxworks.arrctrl.data.api.Movie
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.activity.CheckRow
import com.vknxworks.arrctrl.ui.components.ChoiceSheet
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.nav.AddMovieRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieRoute
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun MoviesScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val radarrs = servers.filter { it.type == ServiceType.RADARR }

    if (radarrs.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_movies))
            MessageView(
                Icons.Outlined.Movie,
                stringResource(R.string.empty_movies_title),
                stringResource(R.string.empty_movies_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
                actionLabel = stringResource(R.string.add_service, stringResource(R.string.service_radarr)),
                onAction = { nav.navigate(ServerEditRoute(type = ServiceType.RADARR.id)) },
            )
        }
        return
    }

    val model: MoviesModel = viewModel { MoviesModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val sort = runCatching { MovieSort.valueOf(settings.moviesSort) }.getOrDefault(MovieSort.TITLE)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var searching by remember { mutableStateOf(model.query.isNotEmpty()) }
    var sortSheet by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkAction?>(null) }
    val server = model.server ?: return

    // Reload whenever the tab comes back into view (after editing, adding or deleting a movie).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(server.id, servers) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { model.load() } }
    }

    val visible = model.visible(sort, settings.moviesSortDescending)
    val selecting = model.selection.isNotEmpty()
    fun open(m: Movie) {
        if (selecting) toggle(model, m.id) else nav.navigate(MovieRoute(m.server.id, m.id))
    }
    fun longPress(m: Movie) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        toggle(model, m.id)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selecting) {
            SelectionBar(
                count = model.selection.size,
                onClose = { model.selection = emptySet() },
                onSelectAll = { model.selection = visible.map { it.id }.toSet() },
                onAction = { bulk = it },
            )
        } else {
            TabTitle(stringResource(R.string.tab_movies)) {
                IconButton(onClick = { searching = !searching; if (!searching) model.query = "" }) {
                    Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, stringResource(R.string.search))
                }
                IconButton(onClick = { sortSheet = true }) { Icon(Icons.Outlined.Sort, stringResource(R.string.sort)) }
                IconButton(onClick = { scope.launch { c.settings.update { it.copy(moviesGrid = !it.moviesGrid) } } }) {
                    Icon(
                        if (settings.moviesGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                        stringResource(if (settings.moviesGrid) R.string.view_list else R.string.view_grid),
                    )
                }
                IconButton(onClick = { nav.navigate(AddMovieRoute(server.id)) }) { Icon(Icons.Outlined.Add, stringResource(R.string.movie_add)) }
            }
        }

        if (radarrs.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                radarrs.forEach { s ->
                    FilterChip(selected = s.id == server.id, onClick = { model.serverId = s.id }, label = { Text(s.name) })
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.query = it },
                placeholder = { Text(stringResource(R.string.movies_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (model.query.isNotEmpty()) IconButton(onClick = { model.query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MovieFilter.entries.forEach { f ->
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
                model.error != null && model.movies.isEmpty() -> Column {
                    ServerErrorCard(server, model.error!!)
                }
                !model.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                visible.isEmpty() -> MessageView(
                    Icons.Outlined.Movie,
                    stringResource(if (model.movies.isEmpty()) R.string.movies_none else R.string.movies_no_match),
                    if (model.movies.isEmpty()) stringResource(R.string.movies_none_body) else null,
                    Modifier.padding(bottom = bottom),
                )
                settings.moviesGrid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    model.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { ServerErrorCard(server, e) } }
                    items(visible, key = { it.key }) { m ->
                        GridTile(m, model, selected = m.id in model.selection, onClick = { open(m) }, onLongClick = { longPress(m) })
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottom)) {
                    model.error?.let { e -> item { ServerErrorCard(server, e) } }
                    items(visible, key = { it.key }) { m ->
                        ListRow(m, model, selected = m.id in model.selection, onClick = { open(m) }, onLongClick = { longPress(m) })
                    }
                }
            }
        }
    }

    if (sortSheet) {
        ChoiceSheet(
            stringResource(R.string.sort),
            MovieSort.entries.flatMap { s ->
                listOf(
                    (s.name to false) to stringResource(s.label) + " " + stringResource(R.string.sort_ascending),
                    (s.name to true) to stringResource(s.label) + " " + stringResource(R.string.sort_descending),
                )
            },
            selected = settings.moviesSort to settings.moviesSortDescending,
            onDismiss = { sortSheet = false },
        ) { (name, desc) ->
            sortSheet = false
            scope.launch { c.settings.update { it.copy(moviesSort = name, moviesSortDescending = desc) } }
        }
    }

    bulk?.let { action ->
        BulkDialog(action, model, onDismiss = { bulk = null }) { result ->
            bulk = null
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
            model.selection = emptySet()
            scope.launch { runCatching { model.load() } }
        }
    }
}

private fun toggle(model: MoviesModel, id: Int) {
    model.selection = if (id in model.selection) model.selection - id else model.selection + id
}

private val MovieFilter.label: Int
    get() = when (this) {
        MovieFilter.ALL -> R.string.filter_all
        MovieFilter.MONITORED -> R.string.filter_monitored
        MovieFilter.UNMONITORED -> R.string.filter_unmonitored
        MovieFilter.MISSING -> R.string.filter_missing
        MovieFilter.DOWNLOADED -> R.string.filter_downloaded
        MovieFilter.DOWNLOADING -> R.string.filter_downloading
    }

private val MovieSort.label: Int
    get() = when (this) {
        MovieSort.TITLE -> R.string.sort_title
        MovieSort.YEAR -> R.string.sort_year
        MovieSort.ADDED -> R.string.sort_added
        MovieSort.RELEASE -> R.string.sort_release
        MovieSort.SIZE -> R.string.sort_size
        MovieSort.RATING -> R.string.sort_rating
    }

@Composable
private fun GridTile(m: Movie, model: MoviesModel, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val api = LocalContext.current.container.radarr(m.server)
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Poster(api.poster(m.id), m.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Box(Modifier.align(Alignment.BottomStart).padding(6.dp)) { BadgeChip(m.badge(m.id in model.downloading)) }
            if (!m.monitored) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    stringResource(R.string.badge_unmonitored),
                    Modifier.align(Alignment.TopEnd).padding(6.dp).background(Scrim, CircleShape).padding(4.dp).size(16.dp),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            if (selected) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.shapes.medium))
                Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.Center).size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(m.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            m.year?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ListRow(m: Movie, model: MoviesModel, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val api = LocalContext.current.container.radarr(m.server)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Poster(api.poster(m.id), "", Modifier.width(54.dp).aspectRatio(2f / 3f))
            if (selected) Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    m.year?.toString(),
                    m.runtime?.let { stringResource(R.string.minutes_short, it) },
                    m.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                    model.profileName(m.qualityProfileId),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BadgeChip(m.badge(m.id in model.downloading))
                if (!m.monitored) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.badge_unmonitored), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- Bulk editing ----------

enum class BulkAction { MONITOR, UNMONITOR, PROFILE, SEARCH, DELETE }

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onAction: (BulkAction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear)) }
        Text(stringResource(R.string.selection_count, count), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.selection_all)) }
        IconButton(onClick = { onAction(BulkAction.MONITOR) }) { Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.bulk_monitor)) }
        IconButton(onClick = { onAction(BulkAction.UNMONITOR) }) { Icon(Icons.Outlined.BookmarkRemove, stringResource(R.string.bulk_unmonitor)) }
        IconButton(onClick = { onAction(BulkAction.PROFILE) }) { Icon(Icons.Outlined.HighQuality, stringResource(R.string.bulk_profile)) }
        IconButton(onClick = { onAction(BulkAction.SEARCH) }) { Icon(Icons.Outlined.Search, stringResource(R.string.bulk_search)) }
        IconButton(onClick = { onAction(BulkAction.DELETE) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.bulk_delete), tint = MaterialTheme.colorScheme.error) }
    }
}

/** Confirms (and for profile and delete, configures) a bulk action, then runs it. [onDone] gets a message to show. */
@Composable
private fun BulkDialog(action: BulkAction, model: MoviesModel, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = model.server ?: return
    val ids = model.selection.toList()
    val api = c.radarr(server)
    val doneText = stringResource(R.string.bulk_done, ids.size)

    fun exec(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { onDone(doneText) }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show(); onDismiss() }
        }
    }

    when (action) {
        BulkAction.PROFILE -> ChoiceSheet(
            stringResource(R.string.bulk_profile),
            model.profiles.map { it.id to it.name },
            selected = null,
            onDismiss = onDismiss,
        ) { id -> exec { api.editor(ids, qualityProfileId = id) } }
        BulkAction.DELETE -> {
            var files by remember { mutableStateOf(false) }
            var exclude by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.movies_delete_title, ids.size, ids.size)) },
                text = {
                    Column {
                        CheckRow(stringResource(R.string.movie_delete_files), files) { files = it }
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
                    BulkAction.MONITOR -> R.string.bulk_monitor
                    BulkAction.UNMONITOR -> R.string.bulk_unmonitor
                    else -> R.string.bulk_search
                },
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.bulk_confirm, ids.size, ids.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        exec {
                            when (action) {
                                BulkAction.MONITOR -> api.editor(ids, monitored = true)
                                BulkAction.UNMONITOR -> api.editor(ids, monitored = false)
                                else -> api.search(ids)
                            }
                        }
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
