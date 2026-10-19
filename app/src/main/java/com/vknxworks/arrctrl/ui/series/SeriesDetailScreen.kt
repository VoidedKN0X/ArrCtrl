package com.vknxworks.arrctrl.ui.series

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.ApiException
import com.vknxworks.arrctrl.data.api.EpisodeRename
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.HistoryItem
import com.vknxworks.arrctrl.data.api.HistoryKind
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.RootFolder
import com.vknxworks.arrctrl.data.api.Series
import com.vknxworks.arrctrl.data.api.SonarrApi
import com.vknxworks.arrctrl.data.api.Tag
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.ui.activity.CheckRow
import com.vknxworks.arrctrl.ui.common.CtrlApps
import com.vknxworks.arrctrl.ui.more.SeriesSubtitlesGroup
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.movies.Poster
import com.vknxworks.arrctrl.ui.movies.Scrim
import com.vknxworks.arrctrl.ui.movies.TagsPicker
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.SeasonRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

val SERIES_TYPES = listOf("standard", "daily", "anime")

@Composable
fun seriesTypeLabel(t: String): String = stringResource(
    when (t) {
        "daily" -> R.string.series_type_daily
        "anime" -> R.string.series_type_anime
        else -> R.string.series_type_standard
    },
)

@Composable
fun seasonName(n: Int): String = if (n == 0) stringResource(R.string.specials) else stringResource(R.string.season_n, n)

@Composable
fun SeriesDetailScreen(serverId: String, seriesId: Int) {
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
    var error by remember { mutableStateOf<TestResult?>(null) }
    var gone by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<RootFolder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    var movingTo by remember { mutableStateOf<String?>(null) }

    // Reload when coming back from a season (its monitoring or files may have changed).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reload) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                val s = async { runCatching { api.series(seriesId) } }
                val h = async { runCatching { api.history(seriesId) } }
                if (profiles.isEmpty()) launch { runCatching { api.profiles() }.onSuccess { profiles = it } }
                if (folders.isEmpty()) launch { runCatching { api.rootFolders() }.onSuccess { folders = it } }
                launch { runCatching { api.tags() }.onSuccess { tags = it } }
                s.await()
                    .onSuccess { if (it == null) gone = true else { series = it; error = null } }
                    .onFailure { e -> if (e is ApiException && e.code == 404) gone = true else error = e.toTestResult() }
                h.await().onSuccess { history = it }
            }
        }
    }

    fun act(done: Int, block: suspend () -> Unit) {
        val msg = context.getString(done)
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); reload++ }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
        }
    }

    val cur = series
    if (gone || cur == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("")
            when {
                gone -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.series_gone))
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type), actionLabel = stringResource(R.string.retry), onAction = { reload++ })
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        return
    }
    val s = cur

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            SeriesHeader(s, api)

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionButton(Icons.Outlined.Search, stringResource(R.string.series_search_all)) { act(R.string.movie_search_started) { api.searchSeries(s.id) } }
                ActionButton(Icons.Outlined.Refresh, stringResource(R.string.movie_refresh)) { act(R.string.movie_refresh_started) { api.refresh(s.id) } }
                if (s.episodeFileCount > 0) ActionButton(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.movie_rename)) { renaming = true }
                if (s.tmdbId != null && CtrlApps.installed(context, CtrlApps.SEERRCTRL)) {
                    ActionButton(Icons.Outlined.OpenInNew, stringResource(R.string.open_in_seerrctrl)) {
                        CtrlApps.open(context, CtrlApps.SEERRCTRL, CtrlApps.seerrTv(s.tmdbId))
                    }
                }
                ActionButton(Icons.Outlined.Delete, stringResource(R.string.movie_delete), danger = true) { confirmDelete = true }
            }

            s.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }

            Group(stringResource(R.string.seasons)) {
                s.seasons.forEach { season ->
                    val pct = if (season.episodeCount <= 0) 0f else (season.episodeFileCount.toFloat() / season.episodeCount).coerceIn(0f, 1f)
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(SeasonRoute(server.id, s.id, season.number)) }.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            act(if (season.monitored) R.string.movie_now_unmonitored else R.string.movie_now_monitored) {
                                api.setSeasonMonitored(s, season.number, !season.monitored)
                            }
                        }) {
                            Icon(
                                if (season.monitored) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                stringResource(if (season.monitored) R.string.season_unmonitor else R.string.season_monitor),
                                tint = if (season.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(seasonName(season.number), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    listOfNotNull(
                                        "${season.episodeFileCount}/${season.episodeCount}",
                                        season.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { pct },
                                color = when {
                                    !season.monitored -> MaterialTheme.colorScheme.outline
                                    season.episodeCount > 0 && season.episodeFileCount >= season.episodeCount -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Group(stringResource(R.string.movie_settings)) {
                GroupSwitch(
                    stringResource(R.string.movie_monitored),
                    stringResource(if (s.monitored) R.string.series_monitored_on else R.string.series_monitored_off),
                    s.monitored,
                ) { on -> act(if (on) R.string.movie_now_monitored else R.string.movie_now_unmonitored) { api.setMonitored(s, on) } }
                GroupChoice(stringResource(R.string.movie_quality_profile), profiles.map { it.id to it.name }, s.qualityProfileId, Icons.Outlined.HighQuality) { id ->
                    act(R.string.saved) { api.update(s) { put("qualityProfileId", JsonPrimitive(id)) } }
                }
                GroupChoice(stringResource(R.string.series_type), SERIES_TYPES.map { it to seriesTypeLabel(it) }, s.seriesType, Icons.Outlined.Category) { t ->
                    act(R.string.saved) { api.update(s) { put("seriesType", JsonPrimitive(t)) } }
                }
                GroupSwitch(stringResource(R.string.series_season_folder), stringResource(R.string.series_season_folder_help), s.seasonFolder, Icons.Outlined.CreateNewFolder) { on ->
                    act(R.string.saved) { api.update(s) { put("seasonFolder", JsonPrimitive(on)) } }
                }
                GroupChoice(
                    stringResource(R.string.movie_root_folder),
                    (folders.map { it.path } + listOfNotNull(s.rootFolderPath)).distinct().map { it to it },
                    s.rootFolderPath,
                    Icons.Outlined.Folder,
                ) { path -> if (path != s.rootFolderPath) movingTo = path }
                GroupRow(
                    stringResource(R.string.movie_tags),
                    s.tags.mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }.joinToString(", ").ifBlank { stringResource(R.string.none) },
                    Icons.Outlined.Label,
                    showChevron = true,
                    onClick = { editingTags = true },
                )
                s.path?.let { GroupRow(stringResource(R.string.detail_path), it) }
            }

            SeriesSubtitlesGroup(s.id, s.tvdbId, s.title)

            val h = history
            if (!h.isNullOrEmpty()) {
                Group(stringResource(R.string.activity_history)) {
                    h.take(25).forEach { item ->
                        GroupRow(
                            item.release ?: item.title,
                            listOfNotNull(item.detail, historyLabel(item.kind), item.quality, item.date?.let { relative(it.toEpochMilli()) }, item.reason).joinToString(" · "),
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = nav::back,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Scrim, contentColor = Color.White),
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
    }

    if (confirmDelete) {
        var files by remember { mutableStateOf(false) }
        var exclude by remember { mutableStateOf(false) }
        val deleted = stringResource(R.string.series_deleted)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.movie_delete_title, s.title)) },
            text = {
                Column {
                    CheckRow(stringResource(R.string.series_delete_files), files) { files = it }
                    CheckRow(stringResource(R.string.movie_add_exclusion), exclude) { exclude = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.delete(s.id, files, exclude) }
                            .onSuccess { Toast.makeText(context, deleted, Toast.LENGTH_SHORT).show(); nav.back() }
                            .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    movingTo?.let { path ->
        AlertDialog(
            onDismissRequest = { movingTo = null },
            title = { Text(stringResource(R.string.movie_move_title)) },
            text = { Text(stringResource(R.string.series_move_body, path)) },
            confirmButton = {
                TextButton(onClick = { movingTo = null; act(R.string.saved) { api.editor(listOf(s.id), rootFolderPath = path, moveFiles = true) } }) {
                    Text(stringResource(R.string.movie_move_files))
                }
            },
            dismissButton = {
                TextButton(onClick = { movingTo = null; act(R.string.saved) { api.editor(listOf(s.id), rootFolderPath = path, moveFiles = false) } }) {
                    Text(stringResource(R.string.movie_move_no))
                }
            },
        )
    }

    if (renaming) SeriesRenameDialog(api, s, onDismiss = { renaming = false }) { renaming = false; reload++ }

    if (editingTags) {
        TagsPicker(tags, s.tags.toSet(), onDismiss = { editingTags = false }) { chosen ->
            editingTags = false
            act(R.string.saved) { api.update(s) { put("tags", JsonArray(chosen.map { JsonPrimitive(it) })) } }
        }
    }
}

@Composable
private fun SeriesHeader(s: Series, api: SonarrApi) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(model = api.fanart(s.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.Bottom) {
        Poster(api.poster(s.id, large = true), s.title, Modifier.width(110.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(s.year?.toString(), s.network, s.runtime?.let { stringResource(R.string.minutes_short, it) }, s.certification).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.genres.isNotEmpty()) Text(s.genres.take(3).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.rating?.let { Text("TVDB " + String.format(java.util.Locale.getDefault(), "%.1f", it), style = MaterialTheme.typography.labelMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(if (s.ended) R.string.series_ended else R.string.series_continuing))
                if (!s.monitored) Chip(stringResource(R.string.badge_unmonitored))
                Text("${s.episodeFileCount}/${s.episodeCount}", style = MaterialTheme.typography.labelMedium)
            }
            s.nextAiring?.let {
                Text(stringResource(R.string.series_next_airing, date(it.toEpochMilli())), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = tint),
        ) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun historyLabel(k: HistoryKind): String = stringResource(
    when (k) {
        HistoryKind.GRABBED -> R.string.history_grabbed
        HistoryKind.IMPORTED -> R.string.history_imported
        HistoryKind.FAILED -> R.string.history_failed
        HistoryKind.DELETED -> R.string.history_deleted
        HistoryKind.RENAMED -> R.string.history_renamed
        HistoryKind.IGNORED -> R.string.history_ignored
        HistoryKind.OTHER -> R.string.history_other
    },
)

fun relative(ms: Long): String = DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun date(ms: Long): String = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))

@Composable
private fun SeriesRenameDialog(api: SonarrApi, s: Series, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<EpisodeRename>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(s.id) { runCatching { api.renamePreview(s.id) }.onSuccess { items = it }.onFailure { failed = true } }
    val started = stringResource(R.string.movie_rename_started)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.movie_rename)) },
        text = {
            val list = items
            when {
                failed -> Text(stringResource(R.string.load_failed))
                list == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text(stringResource(R.string.series_rename_nothing))
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { r ->
                        Text(r.label, style = MaterialTheme.typography.labelLarge)
                        Text(r.existingPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("→ " + r.newPath, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            val list = items
            if (!list.isNullOrEmpty()) {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { api.rename(s.id, list.map { it.episodeFileId }) }
                            .onSuccess { Toast.makeText(context, started, Toast.LENGTH_SHORT).show(); onDone() }
                            .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.movie_rename)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
