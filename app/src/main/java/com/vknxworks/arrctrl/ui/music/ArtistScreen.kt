package com.vknxworks.arrctrl.ui.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album as AlbumIcon
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Album
import com.vknxworks.arrctrl.data.api.ApiException
import com.vknxworks.arrctrl.data.api.Artist
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.HistoryItem
import com.vknxworks.arrctrl.data.api.LidarrApi
import com.vknxworks.arrctrl.data.api.LookupArtist
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.RootFolder
import com.vknxworks.arrctrl.data.api.Tag
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.activity.CheckRow
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.CtrlAlertDialog
import com.vknxworks.arrctrl.ui.components.CtrlBackButton
import com.vknxworks.arrctrl.ui.components.CtrlFilterChip
import com.vknxworks.arrctrl.ui.components.CtrlProgressBar
import com.vknxworks.arrctrl.ui.components.CtrlSheet
import com.vknxworks.arrctrl.ui.components.CtrlTextField
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.ctrlPrimaryButtonColors
import com.vknxworks.arrctrl.ui.components.topDivider
import com.vknxworks.arrctrl.ui.movies.Poster
import com.vknxworks.arrctrl.ui.movies.TagsPicker
import com.vknxworks.arrctrl.ui.nav.AddArtistRoute
import com.vknxworks.arrctrl.ui.nav.ArtistRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.nav.WantedRoute
import com.vknxworks.arrctrl.ui.series.ActionButton
import com.vknxworks.arrctrl.ui.series.date
import com.vknxworks.arrctrl.ui.series.historyLabel
import com.vknxworks.arrctrl.ui.series.relative
import com.vknxworks.arrctrl.ui.servers.failureText
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.CtrlType
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

private val NEW_ITEMS = listOf("all", "new", "none")

@Composable
fun newItemsLabel(v: String): String = stringResource(
    when (v) {
        "new" -> R.string.artist_new_items_new
        "none" -> R.string.artist_new_items_none
        else -> R.string.artist_new_items_all
    },
)

/** One Lidarr artist: artwork, actions, albums (monitor, search), settings and history. */
@Composable
fun ArtistScreen(serverId: String, artistId: Int) {
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
    val api = remember(server) { c.lidarr(server) }

    var artist by remember { mutableStateOf<Artist?>(null) }
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var gone by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var metadata by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryItem>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reload) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                val a = async { runCatching { api.artist(artistId) } }
                val al = async { runCatching { api.albums(artistId) } }
                val h = async { runCatching { api.history(artistId) } }
                if (profiles.isEmpty()) launch { runCatching { api.profiles() }.onSuccess { profiles = it } }
                if (metadata.isEmpty()) launch { runCatching { api.metadataProfiles() }.onSuccess { metadata = it } }
                launch { runCatching { api.tags() }.onSuccess { tags = it } }
                a.await()
                    .onSuccess { if (it == null) gone = true else { artist = it; error = null } }
                    .onFailure { e -> if (e is ApiException && e.code == 404) gone = true else error = e.toTestResult() }
                al.await().onSuccess { albums = it }
                h.await().onSuccess { history = it }
            }
        }
    }

    fun act(done: Int, block: suspend () -> Unit) {
        val msg = context.getString(done)
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); reload++ }
                .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
        }
    }

    val cur = artist
    if (gone || cur == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("")
            when {
                gone -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.artist_gone))
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type), actionLabel = stringResource(R.string.retry), onAction = { reload++ })
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        return
    }
    val a = cur

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(model = api.fanart(a.id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.Bottom) {
                Poster(api.poster(a.id, large = true), a.name, Modifier.width(110.dp).aspectRatio(1f))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(a.artistType, a.disambiguation, a.genres.take(2).joinToString(", ").ifBlank { null }).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip(stringResource(if (a.status == "ended") R.string.series_ended else R.string.series_continuing))
                        if (!a.monitored) Chip(stringResource(R.string.badge_unmonitored))
                        Text("${a.trackFileCount}/${a.trackCount}", style = CtrlType.Mono)
                    }
                    if (a.nextAlbum != null && a.nextAlbumDate != null) {
                        Text(
                            stringResource(R.string.artist_next_album, a.nextAlbum, date(a.nextAlbumDate.toEpochMilli())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionButton(Icons.Outlined.Search, stringResource(R.string.artist_search_all)) { act(R.string.movie_search_started) { api.searchArtist(a.id) } }
                ActionButton(Icons.Outlined.Refresh, stringResource(R.string.movie_refresh)) { act(R.string.movie_refresh_started) { api.refresh(a.id) } }
                ActionButton(Icons.Outlined.Delete, stringResource(R.string.movie_delete), danger = true) { confirmDelete = true }
            }

            a.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp)) }

            val list = albums
            Group(stringResource(R.string.albums)) {
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    list.isEmpty() -> GroupRow(stringResource(R.string.albums_none), stringResource(R.string.albums_none_body))
                    else -> {
                        // Monitored albums first; the rest (other releases the metadata profile allows) on request.
                        val shown = if (showAll) list else list.filter { it.monitored }.ifEmpty { list }
                        shown.forEach { al ->
                            AlbumRow(al, api, onToggle = {
                                act(if (al.monitored) R.string.movie_now_unmonitored else R.string.movie_now_monitored) { api.setAlbumsMonitored(listOf(al.id), !al.monitored) }
                            }, onClick = { openAlbum = al })
                        }
                        val hidden = list.size - shown.size
                        if (hidden > 0 || showAll && list.any { !it.monitored }) {
                            GroupRow(
                                stringResource(if (showAll) R.string.albums_show_monitored else R.string.albums_show_all),
                                if (!showAll) androidx.compose.ui.res.pluralStringResource(R.plurals.albums_unmonitored, hidden, hidden) else null,
                                onClick = { showAll = !showAll },
                            )
                        }
                    }
                }
            }

            Group(stringResource(R.string.movie_settings)) {
                GroupSwitch(
                    stringResource(R.string.movie_monitored),
                    stringResource(if (a.monitored) R.string.artist_monitored_on else R.string.artist_monitored_off),
                    a.monitored,
                ) { on -> act(if (on) R.string.movie_now_monitored else R.string.movie_now_unmonitored) { api.setMonitored(a, on) } }
                GroupChoice(stringResource(R.string.artist_new_items), NEW_ITEMS.map { it to newItemsLabel(it) }, a.monitorNewItems, Icons.Outlined.NewReleases) { v ->
                    act(R.string.saved) { api.update(a) { put("monitorNewItems", JsonPrimitive(v)) } }
                }
                GroupChoice(stringResource(R.string.movie_quality_profile), profiles.map { it.id to it.name }, a.qualityProfileId, Icons.Outlined.HighQuality) { id ->
                    act(R.string.saved) { api.update(a) { put("qualityProfileId", JsonPrimitive(id)) } }
                }
                GroupChoice(stringResource(R.string.artist_metadata_profile), metadata.map { it.id to it.name }, a.metadataProfileId, Icons.Outlined.Tune) { id ->
                    act(R.string.saved) { api.update(a) { put("metadataProfileId", JsonPrimitive(id)) } }
                }
                GroupRow(
                    stringResource(R.string.movie_tags),
                    a.tags.mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }.joinToString(", ").ifBlank { stringResource(R.string.none) },
                    Icons.Outlined.Label,
                    showChevron = true,
                    onClick = { editingTags = true },
                )
                a.path?.let { GroupRow(stringResource(R.string.detail_path), it, Icons.Outlined.Folder) }
                if (a.sizeOnDisk > 0) GroupRow(stringResource(R.string.artist_size), Format.bytes(a.sizeOnDisk))
            }

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
        CtrlBackButton(Modifier.statusBarsPadding().padding(start = Gutter, top = 10.dp), container = CtrlColors.Surface.copy(alpha = 0.88f))
    }

    openAlbum?.let { al ->
        val live = albums?.firstOrNull { it.id == al.id } ?: al
        AlbumSheet(live, api, onDismiss = { openAlbum = null }, onChanged = { reload++ })
    }

    if (confirmDelete) {
        var files by remember { mutableStateOf(false) }
        var exclude by remember { mutableStateOf(false) }
        val deleted = stringResource(R.string.artist_deleted)
        CtrlAlertDialog(
            shielded = true,
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.movie_delete_title, a.name)) },
            text = {
                Column {
                    CheckRow(stringResource(R.string.artist_delete_files), files) { files = it }
                    CheckRow(stringResource(R.string.movie_add_exclusion), exclude) { exclude = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { api.delete(a.id, files, exclude) }
                            .onSuccess { Toast.makeText(context, deleted, Toast.LENGTH_SHORT).show(); nav.back() }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editingTags) {
        TagsPicker(tags, a.tags.toSet(), onDismiss = { editingTags = false }) { chosen ->
            editingTags = false
            act(R.string.saved) { api.update(a) { put("tags", JsonArray(chosen.map { JsonPrimitive(it) })) } }
        }
    }
}

@Composable
private fun AlbumRow(al: Album, api: LidarrApi, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topDivider(CtrlColors.CardBorder).clickable(onClick = onClick).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (al.monitored) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                stringResource(if (al.monitored) R.string.album_unmonitor else R.string.album_monitor),
                tint = if (al.monitored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Poster(api.cover(al.id), "", Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(al.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(al.year?.toString(), al.albumType, if (al.trackCount > 0) "${al.trackFileCount}/${al.trackCount}" else null).joinToString(" · "),
                style = CtrlType.Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (al.monitored && al.trackCount > 0) {
                CtrlProgressBar(
                    progress = { al.progress },
                    color = if (al.complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    height = 4.dp,
                )
            }
        }
    }
}

@Composable
private fun AlbumSheet(al: Album, api: LidarrApi, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun run(done: Int, block: suspend () -> Unit) {
        val msg = context.getString(done)
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); onChanged(); onDismiss() }
                .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
        }
    }
    CtrlSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(api.cover(al.id), al.title, Modifier.size(96.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(al.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    al.disambiguation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        listOfNotNull(
                            al.releaseDate?.let { date(it.toEpochMilli()) },
                            (listOfNotNull(al.albumType) + al.secondaryTypes).joinToString(", ").ifBlank { null },
                        ).joinToString(" · "),
                        style = CtrlType.Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            if (al.trackCount > 0) stringResource(R.string.album_tracks, al.trackFileCount, al.trackCount) else null,
                            al.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                            al.durationMs?.let { stringResource(R.string.minutes_short, (it / 60_000).toInt()) },
                        ).joinToString(" · "),
                        style = CtrlType.Mono,
                    )
                }
            }
            Group {
                GroupRow(stringResource(R.string.album_search), stringResource(R.string.album_search_help), Icons.Outlined.Search, onClick = {
                    run(R.string.movie_search_started) { api.searchAlbums(listOf(al.id)) }
                })
                GroupSwitch(
                    stringResource(R.string.movie_monitored),
                    stringResource(if (al.monitored) R.string.album_monitored_on else R.string.album_monitored_off),
                    al.monitored,
                ) { on -> run(if (on) R.string.movie_now_monitored else R.string.movie_now_unmonitored) { api.setAlbumsMonitored(listOf(al.id), on) } }
            }
        }
    }
}
