package com.vknxworks.arrctrl.ui.movies

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.HistoryItem
import com.vknxworks.arrctrl.data.api.Movie
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.RadarrApi
import com.vknxworks.arrctrl.data.api.RenameItem
import com.vknxworks.arrctrl.data.api.RootFolder
import com.vknxworks.arrctrl.data.api.Tag
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.ui.activity.CheckRow
import com.vknxworks.arrctrl.ui.activity.Detail
import com.vknxworks.arrctrl.ui.common.CtrlApps
import com.vknxworks.arrctrl.ui.more.MovieSubtitlesGroup
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieReleasesRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private val AVAILABILITY = listOf("announced", "inCinemas", "released")

@Composable
fun MovieScreen(serverId: String, movieId: Int) {
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
    val api = remember(server) { c.radarr(server) }

    var movie by remember { mutableStateOf<Movie?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var gone by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<RootFolder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteFile by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    var movingTo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        coroutineScope {
            val m = async { runCatching { api.movie(movieId) } }
            val h = async { runCatching { api.history(movieId) } }
            if (profiles.isEmpty()) launch { runCatching { api.profiles() }.onSuccess { profiles = it } }
            if (folders.isEmpty()) launch { runCatching { api.rootFolders() }.onSuccess { folders = it } }
            launch { runCatching { api.tags() }.onSuccess { tags = it } }
            m.await()
                .onSuccess { if (it == null) gone = true else { movie = it; error = null } }
                .onFailure { e ->
                    if (e is com.vknxworks.arrctrl.data.api.ApiException && e.code == 404) gone = true else error = e.toTestResult()
                }
            h.await().onSuccess { history = it }
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

    val m = movie
    when {
        gone -> {
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar("")
                MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.movie_gone))
            }
            return
        }
        m == null -> {
            Column(Modifier.fillMaxSize()) {
                ScreenTopBar("")
                if (error != null) MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type), actionLabel = stringResource(R.string.retry), onAction = { reload++ })
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            return
        }
    }
    val mv = m!!

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Header(mv, api)

            // Actions
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Action(Icons.Outlined.Search, stringResource(R.string.movie_search)) { act(R.string.movie_search_started) { api.search(listOf(mv.id)) } }
                Action(Icons.Outlined.ManageSearch, stringResource(R.string.movie_interactive)) {
                    nav.navigate(MovieReleasesRoute(server.id, mv.id, mv.title))
                }
                Action(Icons.Outlined.Refresh, stringResource(R.string.movie_refresh)) { act(R.string.movie_refresh_started) { api.refresh(listOf(mv.id)) } }
                if (mv.hasFile) Action(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.movie_rename)) { renaming = true }
                if (mv.hasFile && mv.tmdbId != null && CtrlApps.installed(context, CtrlApps.JELLYCTRL)) {
                    Action(Icons.Outlined.PlayArrow, stringResource(R.string.play_in_jellyctrl)) {
                        CtrlApps.open(context, CtrlApps.JELLYCTRL, CtrlApps.jellyMovie(mv.tmdbId))
                    }
                }
                if (mv.tmdbId != null && CtrlApps.installed(context, CtrlApps.SEERRCTRL)) {
                    Action(Icons.Outlined.OpenInNew, stringResource(R.string.open_in_seerrctrl)) {
                        CtrlApps.open(context, CtrlApps.SEERRCTRL, CtrlApps.seerrMovie(mv.tmdbId))
                    }
                }
                Action(Icons.Outlined.Delete, stringResource(R.string.movie_delete), danger = true) { confirmDelete = true }
            }

            mv.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
            }

            Group(stringResource(R.string.movie_settings)) {
                GroupSwitch(
                    stringResource(R.string.movie_monitored),
                    stringResource(if (mv.monitored) R.string.movie_monitored_on else R.string.movie_monitored_off),
                    mv.monitored,
                ) { on -> act(if (on) R.string.movie_now_monitored else R.string.movie_now_unmonitored) { api.setMonitored(mv, on) } }
                GroupChoice(
                    stringResource(R.string.movie_quality_profile),
                    profiles.map { it.id to it.name },
                    mv.qualityProfileId,
                    Icons.Outlined.HighQuality,
                ) { id -> act(R.string.saved) { api.update(mv) { put("qualityProfileId", JsonPrimitive(id)) } } }
                GroupChoice(
                    stringResource(R.string.movie_min_availability),
                    AVAILABILITY.map { it to availabilityLabel(it) },
                    mv.minimumAvailability,
                    Icons.Outlined.Schedule,
                ) { v -> act(R.string.saved) { api.update(mv) { put("minimumAvailability", JsonPrimitive(v)) } } }
                GroupChoice(
                    stringResource(R.string.movie_root_folder),
                    (folders.map { it.path } + listOfNotNull(mv.rootFolderPath)).distinct().map { it to it },
                    mv.rootFolderPath,
                    Icons.Outlined.Folder,
                ) { path -> if (path != mv.rootFolderPath) movingTo = path }
                GroupRow(
                    stringResource(R.string.movie_tags),
                    mv.tags.mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }.joinToString(", ").ifBlank { stringResource(R.string.none) },
                    Icons.Outlined.Label,
                    showChevron = true,
                    onClick = { editingTags = true },
                )
                mv.path?.let { GroupRow(stringResource(R.string.detail_path), it) }
            }

            val f = mv.file
            if (f != null) {
                Group(stringResource(R.string.movie_file)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.relativePath?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        f.quality?.let { Detail(R.string.detail_quality, if (f.cutoffNotMet) it + " · " + stringResource(R.string.movie_cutoff_unmet) else it) }
                        Detail(R.string.detail_size, Format.bytes(f.size))
                        listOfNotNull(f.resolution, f.videoCodec, f.videoDynamicRange).joinToString(" · ").takeIf { it.isNotBlank() }
                            ?.let { Detail(R.string.detail_video, it) }
                        listOfNotNull(f.audioCodec, f.audioChannels?.let { ch -> String.format(java.util.Locale.ROOT, "%.1f", ch) }, f.audioLanguages)
                            .joinToString(" · ").takeIf { it.isNotBlank() }?.let { Detail(R.string.detail_audio, it) }
                        f.subtitles?.let { Detail(R.string.detail_subtitles, it) }
                        f.releaseGroup?.let { Detail(R.string.detail_release_group, it) }
                        f.edition?.let { Detail(R.string.detail_edition, it) }
                        f.customFormatScore?.takeIf { it != 0 }?.let { Detail(R.string.detail_cf_score, signed(it)) }
                        f.dateAdded?.let { Detail(R.string.detail_added, relative(it.toEpochMilli())) }
                    }
                    GroupRow(
                        stringResource(R.string.movie_delete_file),
                        icon = Icons.Outlined.DeleteOutline,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { confirmDeleteFile = true },
                    )
                }
            }

            MovieSubtitlesGroup(mv.id, mv.imdbId, mv.title, mv.hasFile)

            Group(stringResource(R.string.movie_release_dates)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Detail(R.string.movie_in_cinemas, mv.inCinemas?.let { date(it.toEpochMilli()) } ?: "–")
                    Detail(R.string.movie_digital, mv.digitalRelease?.let { date(it.toEpochMilli()) } ?: "–")
                    Detail(R.string.movie_physical, mv.physicalRelease?.let { date(it.toEpochMilli()) } ?: "–")
                    mv.added?.let { Detail(R.string.detail_added, date(it.toEpochMilli())) }
                }
            }

            val h = history
            if (!h.isNullOrEmpty()) {
                Group(stringResource(R.string.activity_history)) {
                    h.take(20).forEach { item ->
                        GroupRow(
                            item.release ?: item.title,
                            listOfNotNull(historyLabel(item.kind), item.quality, item.date?.let { relative(it.toEpochMilli()) }, item.reason).joinToString(" · "),
                        )
                    }
                }
            }
        }

        // Back button over the artwork.
        IconButton(
            onClick = nav::back,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Scrim, contentColor = Color.White),
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
    }

    if (confirmDelete) {
        var files by remember { mutableStateOf(false) }
        var exclude by remember { mutableStateOf(false) }
        val deleted = stringResource(R.string.movie_deleted)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.movie_delete_title, mv.title)) },
            text = {
                Column {
                    CheckRow(stringResource(R.string.movie_delete_files), files) { files = it }
                    CheckRow(stringResource(R.string.movie_add_exclusion), exclude) { exclude = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.delete(mv.id, files, exclude) }
                            .onSuccess { Toast.makeText(context, deleted, Toast.LENGTH_SHORT).show(); nav.back() }
                            .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDeleteFile && mv.file != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = false },
            title = { Text(stringResource(R.string.movie_delete_file_title)) },
            text = { Text(mv.file.relativePath ?: mv.title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteFile = false
                    act(R.string.movie_file_deleted) { api.deleteFile(mv.file.id) }
                }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFile = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    movingTo?.let { path ->
        AlertDialog(
            onDismissRequest = { movingTo = null },
            title = { Text(stringResource(R.string.movie_move_title)) },
            text = { Text(stringResource(R.string.movie_move_body, path)) },
            confirmButton = {
                TextButton(onClick = {
                    movingTo = null
                    act(R.string.saved) { api.editor(listOf(mv.id), rootFolderPath = path, moveFiles = true) }
                }) { Text(stringResource(R.string.movie_move_files)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    movingTo = null
                    act(R.string.saved) { api.editor(listOf(mv.id), rootFolderPath = path, moveFiles = false) }
                }) { Text(stringResource(R.string.movie_move_no)) }
            },
        )
    }

    if (renaming) RenameDialog(api, mv, onDismiss = { renaming = false }) { renaming = false; reload++ }

    if (editingTags) {
        TagsPicker(tags, mv.tags.toSet(), onDismiss = { editingTags = false }) { chosen ->
            editingTags = false
            act(R.string.saved) { api.update(mv) { put("tags", JsonArray(chosen.map { JsonPrimitive(it) })) } }
        }
    }
}

@Composable
private fun Header(m: Movie, api: RadarrApi) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = api.fanart(m.id),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
            ),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 0.dp), verticalAlignment = Alignment.Bottom) {
        Poster(api.poster(m.id, large = true), m.title, Modifier.width(110.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(m.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.year?.toString(), m.runtime?.let { stringResource(R.string.minutes_short, it) }, m.certification).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (m.genres.isNotEmpty()) {
                Text(m.genres.take(3).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val ratings = listOfNotNull(
                m.imdbRating?.let { "IMDb " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
                m.tmdbRating?.let { "TMDB " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
                m.rottenTomatoes?.let { "RT ${it.toInt()}%" },
            )
            if (ratings.isNotEmpty()) Text(ratings.joinToString("  "), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BadgeChip(m.badge(false))
                if (!m.monitored) com.vknxworks.arrctrl.ui.components.Chip(stringResource(R.string.badge_unmonitored))
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = tint),
        ) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun availabilityLabel(v: String): String = stringResource(
    when (v) {
        "announced" -> R.string.availability_announced
        "inCinemas" -> R.string.availability_in_cinemas
        else -> R.string.availability_released
    },
)

@Composable
private fun historyLabel(k: com.vknxworks.arrctrl.data.api.HistoryKind): String = stringResource(
    when (k) {
        com.vknxworks.arrctrl.data.api.HistoryKind.GRABBED -> R.string.history_grabbed
        com.vknxworks.arrctrl.data.api.HistoryKind.IMPORTED -> R.string.history_imported
        com.vknxworks.arrctrl.data.api.HistoryKind.FAILED -> R.string.history_failed
        com.vknxworks.arrctrl.data.api.HistoryKind.DELETED -> R.string.history_deleted
        com.vknxworks.arrctrl.data.api.HistoryKind.RENAMED -> R.string.history_renamed
        com.vknxworks.arrctrl.data.api.HistoryKind.IGNORED -> R.string.history_ignored
        com.vknxworks.arrctrl.data.api.HistoryKind.OTHER -> R.string.history_other
    },
)

fun signed(n: Int): String = if (n > 0) "+$n" else n.toString()

private fun relative(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun date(ms: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))

@Composable
private fun RenameDialog(api: RadarrApi, m: Movie, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<RenameItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(m.id) { runCatching { api.renamePreview(m.id) }.onSuccess { items = it }.onFailure { failed = true } }
    val started = stringResource(R.string.movie_rename_started)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.movie_rename)) },
        text = {
            val list = items
            when {
                failed -> Text(stringResource(R.string.load_failed))
                list == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text(stringResource(R.string.movie_rename_nothing))
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { r ->
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
                        runCatching { api.rename(m.id, list.map { it.movieFileId }) }
                            .onSuccess { Toast.makeText(context, started, Toast.LENGTH_SHORT).show(); onDone() }
                            .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.movie_rename)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TagsPicker(all: List<Tag>, initial: Set<Int>, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.movie_tags)) },
        text = {
            if (all.isEmpty()) Text(stringResource(R.string.tags_none))
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                all.sortedBy { it.label.lowercase() }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(t.id in chosen, onCheckedChange = { on -> chosen = if (on) chosen + t.id else chosen - t.id })
                        Text(t.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
