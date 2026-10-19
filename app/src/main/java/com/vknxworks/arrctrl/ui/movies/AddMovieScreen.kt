package com.vknxworks.arrctrl.ui.movies

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.LookupMovie
import com.vknxworks.arrctrl.data.api.Profile
import com.vknxworks.arrctrl.data.api.RadarrApi
import com.vknxworks.arrctrl.data.api.RootFolder
import com.vknxworks.arrctrl.data.api.Tag
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private val MONITOR = listOf("movieOnly", "movieAndCollection", "none")

@Composable
fun AddMovieScreen(serverId: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.movie_add))
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.radarr(server) }

    var term by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LookupMovie>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var picked by remember { mutableStateOf<LookupMovie?>(null) }

    fun search() {
        val t = term.trim()
        if (t.isEmpty()) return
        focus.clearFocus()
        searching = true
        error = null
        scope.launch {
            runCatching { api.lookup(t) }
                .onSuccess { results = it }
                .onFailure { error = it.toTestResult() }
            searching = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.movie_add)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                placeholder = { Text(stringResource(R.string.movie_add_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            )
            val list = results
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type))
                list == null -> MessageView(Icons.Outlined.Movie, stringResource(R.string.movie_add_intro), stringResource(R.string.movie_add_intro_body))
                list.isEmpty() -> MessageView(Icons.Outlined.Search, stringResource(R.string.movie_add_nothing))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { (it.tmdbId ?: it.title.hashCode()).toString() + it.year }) { m ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val id = m.inLibraryId
                                    if (id != null) nav.navigate(MovieRoute(server.id, id)) else picked = m
                                }
                                .padding(horizontal = Gutter, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                m.year?.let { "${m.title} ($it)" } ?: m.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val meta = listOfNotNull(
                                m.runtime?.let { stringResource(R.string.minutes_short, it) },
                                m.genres.take(3).joinToString(", ").ifBlank { null },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            m.overview?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            if (m.inLibraryId != null) Chip(stringResource(R.string.movie_in_library), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                        }
                    }
                }
            }
        }
    }

    picked?.let { m ->
        AddMovieSheet(api, m, onDismiss = { picked = null }) { created ->
            picked = null
            if (created != null) nav.navigate(MovieRoute(server.id, created)) else nav.back()
        }
    }
}

@Composable
private fun AddMovieSheet(api: RadarrApi, m: LookupMovie, onDismiss: () -> Unit, onAdded: (Int?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var folders by remember { mutableStateOf<List<RootFolder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var profileId by remember { mutableStateOf<Int?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var monitor by remember { mutableStateOf("movieOnly") }
    var availability by remember { mutableStateOf("released") }
    var chosenTags by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var searchNow by remember { mutableStateOf(true) }
    var editingTags by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        runCatching { api.profiles() }.onSuccess { profiles = it; if (profileId == null) profileId = it.firstOrNull()?.id }
        runCatching { api.rootFolders() }.onSuccess { folders = it; if (folder == null) folder = it.firstOrNull()?.path }
        runCatching { api.tags() }.onSuccess { tags = it }
    }

    val addedText = stringResource(R.string.movie_added)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                m.year?.let { "${m.title} ($it)" } ?: m.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Group {
                GroupChoice(stringResource(R.string.movie_quality_profile), profiles.map { it.id to it.name }, profileId, Icons.Outlined.HighQuality) { profileId = it }
                GroupChoice(
                    stringResource(R.string.movie_root_folder),
                    folders.map { f -> f.path to (f.freeSpace?.let { "${f.path} · " + context.getString(R.string.free_space, Format.bytes(it)) } ?: f.path) },
                    folder,
                    Icons.Outlined.Folder,
                ) { folder = it }
                GroupChoice(stringResource(R.string.movie_monitor), MONITOR.map { it to monitorLabel(it) }, monitor, Icons.Outlined.Visibility) { monitor = it }
                GroupChoice(stringResource(R.string.movie_min_availability), listOf("announced", "inCinemas", "released").map { it to availabilityLabel(it) }, availability, Icons.Outlined.Schedule) { availability = it }
                GroupRow(
                    stringResource(R.string.movie_tags),
                    chosenTags.mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }.joinToString(", ").ifBlank { stringResource(R.string.none) },
                    Icons.Outlined.Label,
                    showChevron = true,
                    onClick = { editingTags = true },
                )
                GroupSwitch(stringResource(R.string.movie_search_on_add), stringResource(R.string.movie_search_on_add_help), searchNow) { searchNow = it }
            }
            Button(
                onClick = {
                    val p = profileId
                    val f = folder
                    if (p != null && f != null) {
                    adding = true
                    scope.launch {
                        runCatching { api.add(m, p, f, monitor, availability, searchNow, chosenTags.toList()) }
                            .onSuccess {
                                context.container.telemetry.action("add", "radarr")
                                Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
                                onAdded(it?.id)
                            }
                            .onFailure {
                                adding = false
                                Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show()
                            }
                    }
                    }
                },
                enabled = !adding && profileId != null && folder != null,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.movie_add_button)) }
        }
    }

    if (editingTags) {
        TagsPicker(tags, chosenTags, onDismiss = { editingTags = false }) { chosenTags = it.toSet(); editingTags = false }
    }
}

@Composable
private fun monitorLabel(v: String): String = stringResource(
    when (v) {
        "movieAndCollection" -> R.string.monitor_movie_collection
        "none" -> R.string.monitor_none
        else -> R.string.monitor_movie
    },
)
