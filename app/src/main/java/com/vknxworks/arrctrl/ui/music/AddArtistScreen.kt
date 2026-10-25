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

/** Lidarr's monitor options when adding an artist (MonitorTypes), in the web UI's order. */
private val MONITOR = listOf("all", "future", "missing", "existing", "first", "latest", "none")

@Composable
private fun monitorLabel(v: String): String = stringResource(
    when (v) {
        "future" -> R.string.artist_monitor_future
        "missing" -> R.string.artist_monitor_missing
        "existing" -> R.string.artist_monitor_existing
        "first" -> R.string.artist_monitor_first
        "latest" -> R.string.artist_monitor_latest
        "none" -> R.string.artist_monitor_none
        else -> R.string.artist_monitor_all
    },
)

private val NEW_ITEMS_ADD = listOf("all", "new", "none")

@Composable
fun AddArtistScreen(serverId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.artist_add))
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.lidarr(server) }

    var term by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LookupArtist>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var picked by remember { mutableStateOf<LookupArtist?>(null) }

    fun search() {
        val t = term.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        searching = true
        error = null
        scope.launch {
            runCatching { api.lookup(t) }.onSuccess { results = it }.onFailure { error = it.toTestResult() }
            searching = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.artist_add)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            CtrlTextField(
                value = term,
                onValueChange = { term = it },
                placeholder = { Text(stringResource(R.string.artist_add_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            )
            val list = results
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type))
                list == null -> MessageView(Icons.Outlined.LibraryMusic, stringResource(R.string.artist_add_intro), stringResource(R.string.artist_add_intro_body))
                list.isEmpty() -> MessageView(Icons.Outlined.Search, stringResource(R.string.movie_add_nothing))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { it.foreignArtistId ?: (it.name + it.disambiguation) }) { a ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val id = a.inLibraryId
                                    if (id != null) nav.navigate(ArtistRoute(server.id, id)) else picked = a
                                }
                                .padding(horizontal = Gutter, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall)
                            val meta = listOfNotNull(a.artistType, a.disambiguation, a.genres.take(3).joinToString(", ").ifBlank { null }).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            a.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            if (a.inLibraryId != null) Chip(stringResource(R.string.movie_in_library), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                        }
                    }
                }
            }
        }
    }

    picked?.let { a ->
        AddArtistSheet(api, a, onDismiss = { picked = null }) { created ->
            picked = null
            if (created != null) nav.navigate(ArtistRoute(server.id, created)) else nav.back()
        }
    }
}

@Composable
private fun AddArtistSheet(api: LidarrApi, a: LookupArtist, onDismiss: () -> Unit, onAdded: (Int?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var metadata by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<RootFolder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var profileId by remember { mutableStateOf<Int?>(null) }
    var metadataId by remember { mutableStateOf<Int?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var monitor by remember { mutableStateOf("all") }
    var newItems by remember { mutableStateOf("all") }
    var chosenTags by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var searchNow by remember { mutableStateOf(true) }
    var editingTags by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        runCatching { api.profiles() }.onSuccess { profiles = it; if (profileId == null) profileId = it.firstOrNull()?.id }
        runCatching { api.metadataProfiles() }.onSuccess { metadata = it; if (metadataId == null) metadataId = it.firstOrNull()?.id }
        runCatching { api.rootFolders() }.onSuccess { folders = it; if (folder == null) folder = it.firstOrNull()?.path }
        runCatching { api.tags() }.onSuccess { tags = it }
    }

    val addedText = stringResource(R.string.artist_added)
    CtrlSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Group {
                GroupChoice(stringResource(R.string.movie_quality_profile), profiles.map { it.id to it.name }, profileId, Icons.Outlined.HighQuality) { profileId = it }
                GroupChoice(stringResource(R.string.artist_metadata_profile), metadata.map { it.id to it.name }, metadataId, Icons.Outlined.Tune) { metadataId = it }
                GroupChoice(
                    stringResource(R.string.movie_root_folder),
                    folders.map { f -> f.path to (f.freeSpace?.let { context.getString(R.string.dot_pair, f.path, context.getString(R.string.free_space, Format.bytes(it))) } ?: f.path) },
                    folder,
                    Icons.Outlined.Folder,
                ) { folder = it }
                GroupChoice(stringResource(R.string.movie_monitor), MONITOR.map { it to monitorLabel(it) }, monitor, Icons.Outlined.Visibility) { monitor = it }
                GroupChoice(stringResource(R.string.artist_new_items), NEW_ITEMS_ADD.map { it to newItemsLabel(it) }, newItems, Icons.Outlined.NewReleases) { newItems = it }
                GroupRow(
                    stringResource(R.string.movie_tags),
                    chosenTags.mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }.joinToString(", ").ifBlank { stringResource(R.string.none) },
                    Icons.Outlined.Label,
                    showChevron = true,
                    onClick = { editingTags = true },
                )
                GroupSwitch(stringResource(R.string.artist_search_on_add), stringResource(R.string.artist_search_on_add_help), searchNow) { searchNow = it }
            }
            Button(
                colors = ctrlPrimaryButtonColors(),
                onClick = {
                    val p = profileId
                    val m = metadataId
                    val f = folder
                    if (p != null && m != null && f != null) {
                        adding = true
                        scope.launch {
                            runCatching { api.add(a, p, m, f, monitor, newItems, searchNow, chosenTags.toList()) }
                                .onSuccess {
                                    Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
                                    onAdded(it?.id)
                                }
                                .onFailure {
                                    adding = false
                                    Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                },
                enabled = !adding && profileId != null && metadataId != null && folder != null,
                shape = CtrlShapes.Button,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.artist_add_button)) }
        }
    }

    if (editingTags) {
        TagsPicker(tags, chosenTags, onDismiss = { editingTags = false }) { chosenTags = it.toSet(); editingTags = false }
    }
}
