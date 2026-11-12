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

enum class ArtistFilter(val label: Int) {
    ALL(R.string.filter_all), MONITORED(R.string.filter_monitored), UNMONITORED(R.string.filter_unmonitored),
    MISSING(R.string.filter_missing), DOWNLOADING(R.string.filter_downloading),
}

/**
 * Music (More → Library → Music, only with a Lidarr server): your Lidarr artists, one server at a time, with
 * search, filters and add. Tap an artist for its albums.
 */
@Composable
fun MusicScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val all by c.servers.servers.collectAsStateWithLifecycle()
    val lidarrs = all.filter { it.type == ServiceType.LIDARR }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val server = lidarrs.firstOrNull { it.id == chosen } ?: lidarrs.firstOrNull()

    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.music))
            MessageView(
                Icons.Outlined.LibraryMusic,
                stringResource(R.string.empty_music_title),
                stringResource(R.string.empty_music_body),
                actionLabel = stringResource(R.string.add_service, stringResource(R.string.service_lidarr)),
                onAction = { nav.navigate(ServerEditRoute(type = ServiceType.LIDARR.id)) },
            )
        }
        return
    }
    val api = remember(server) { c.lidarr(server) }

    var artists by remember(server.id) { mutableStateOf<List<Artist>>(emptyList()) }
    var downloading by remember(server.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var loaded by remember(server.id) { mutableStateOf(false) }
    var error by remember(server.id) { mutableStateOf<TestResult?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf(ArtistFilter.ALL.name) }
    val filter = ArtistFilter.valueOf(filterName)
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() = coroutineScope {
        val a = async { runCatching { api.artists() } }
        val q = async { runCatching { api.queuedArtistIds() } }
        val r = a.await()
        r.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        r.onSuccess { artists = it.sortedBy { x -> x.sortName }; error = null }.onFailure { error = it.toTestResult() }
        q.await().onSuccess { downloading = it }
        loaded = true
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(server.id) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { load() } } }

    val visible = artists.filter { a ->
        (query.isBlank() || a.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
            ArtistFilter.ALL -> true
            ArtistFilter.MONITORED -> a.monitored
            ArtistFilter.UNMONITORED -> !a.monitored
            ArtistFilter.MISSING -> a.monitored && a.trackCount > 0 && !a.complete
            ArtistFilter.DOWNLOADING -> a.id in downloading
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.music)) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, stringResource(R.string.search))
            }
            IconButton(onClick = { nav.navigate(WantedRoute(server.id)) }) { Icon(Icons.Outlined.ManageSearch, stringResource(R.string.wanted)) }
            IconButton(onClick = { nav.navigate(AddArtistRoute(server.id)) }) { Icon(Icons.Outlined.Add, stringResource(R.string.artist_add)) }
        }
        if (lidarrs.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lidarrs.forEach { s -> CtrlFilterChip(selected = s.id == server.id, onClick = { chosen = s.id }, label = { Text(s.name) }) }
            }
        }
        if (searching) {
            CtrlTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.artists_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtistFilter.entries.forEach { f ->
                CtrlFilterChip(selected = f == filter, onClick = { filterName = f.name }, label = { Text(stringResource(f.label)) })
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; runCatching { load() }; refreshing = false } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                error != null && artists.isEmpty() -> Column { ServerErrorCard(server, error!!) }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                visible.isEmpty() -> MessageView(
                    Icons.Outlined.LibraryMusic,
                    stringResource(if (artists.isEmpty()) R.string.artists_none else R.string.movies_no_match),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                    error?.let { e -> item { ServerErrorCard(server, e) } }
                    items(visible, key = { it.key }) { a ->
                        ArtistRow(a, api, a.id in downloading) { nav.navigate(ArtistRoute(server.id, a.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(a: Artist, api: LidarrApi, downloading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(api.poster(a.id), "", Modifier.width(54.dp).aspectRatio(1f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.album_count, a.albumCount, a.albumCount),
                    "${a.trackFileCount}/${a.trackCount}",
                    a.sizeOnDisk.takeIf { it > 0 }?.let { Format.bytes(it) },
                ).joinToString(" · "),
                style = CtrlType.Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            CtrlProgressBar(
                progress = { a.progress },
                color = when {
                    !a.monitored -> CtrlColors.Stopped
                    a.complete -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxWidth(),
                height = 4.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (downloading) Chip(stringResource(R.string.badge_downloading), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                if (!a.monitored) Chip(stringResource(R.string.badge_unmonitored))
            }
        }
    }
}
