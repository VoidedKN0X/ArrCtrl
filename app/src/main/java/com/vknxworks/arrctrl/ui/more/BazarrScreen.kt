package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.BazarrApi
import com.vknxworks.arrctrl.data.api.LanguagesProfile
import com.vknxworks.arrctrl.data.api.SubHistory
import com.vknxworks.arrctrl.data.api.SubProvider
import com.vknxworks.arrctrl.data.api.SubtitleResult
import com.vknxworks.arrctrl.data.api.WantedItem
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.SubtitleSearchRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val PAGE = 50

@Composable
fun BazarrScreen() = ToolScaffold(stringResource(R.string.service_bazarr), ServiceType.BAZARR) { server ->
    val c = LocalContext.current.container
    val api = remember(server) { c.bazarr(server) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf(R.string.bazarr_wanted, R.string.activity_history, R.string.bazarr_providers).forEachIndexed { i, l ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(l)) })
            }
        }
        when (tab) {
            0 -> WantedTab(server, api)
            1 -> SubHistoryTab(server, api)
            else -> ProvidersTab(server, api)
        }
    }
}

@Composable
private fun WantedTab(server: Server, api: BazarrApi) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var episodes by rememberSaveable { mutableStateOf(false) }
    var items by remember(episodes) { mutableStateOf<List<WantedItem>?>(null) }
    var total by remember(episodes) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<WantedItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var languagesFor by remember { mutableStateOf<WantedItem?>(null) }

    suspend fun load(reset: Boolean) {
        val start = if (reset) 0 else items.orEmpty().size
        runCatching { if (episodes) api.wantedEpisodes(start, PAGE) else api.wantedMovies(start, PAGE) }
            .onSuccess { (page, t) -> items = if (reset) page else items.orEmpty() + page; total = t; error = null }
            .onFailure { error = it.toTestResult() }
    }
    LaunchedEffect(episodes) { load(reset = true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(selected = !episodes, onClick = { episodes = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.tab_movies)) }
                SegmentedButton(selected = episodes, onClick = { episodes = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.bazarr_episodes)) }
            }
            IconButton(onClick = { confirmAll = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.bazarr_search_all)) }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val l = items
            when {
                l == null && error != null -> Column { ServerErrorCard(server, error!!) }
                l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                l.isEmpty() -> MessageView(Icons.Outlined.CheckCircle, stringResource(R.string.bazarr_nothing_wanted))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    error?.let { e -> item { ServerErrorCard(server, e) } }
                    item {
                        Text(
                            stringResource(R.string.bazarr_wanted_count, total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                        )
                    }
                    items(l, key = { it.key }) { w ->
                        Column(Modifier.fillMaxWidth().clickable { open = w }.padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(w.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            w.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                w.missing.forEach { Chip(it.label, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
                            }
                        }
                    }
                    if (l.size < total) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else OutlinedButton(onClick = { scope.launch { loadingMore = true; load(false); loadingMore = false } }, shape = CircleShape) {
                                    Text(stringResource(R.string.history_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val started = stringResource(R.string.movie_search_started)
    fun exec(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { Toast.makeText(context, started, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
        }
    }

    open?.let { w ->
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(w.title, style = MaterialTheme.typography.titleLarge)
                w.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(stringResource(R.string.bazarr_missing, w.missing.joinToString(", ") { it.name.ifBlank { it.label } }), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    onClick = {
                        open = null
                        val radarr = w.radarrId
                        val series = w.seriesId
                        when {
                            radarr != null -> exec { api.searchMovie(radarr) }
                            series != null -> exec { api.searchSeries(series) }
                            else -> Unit
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, null)
                    Text(stringResource(if (w.radarrId != null) R.string.bazarr_search_auto else R.string.bazarr_search_series), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        open = null
                        nav.navigate(SubtitleSearchRoute(server.id, w.radarrId, w.seriesId, w.episodeId, listOfNotNull(w.title, w.detail).joinToString(" · ")))
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ManageSearch, null)
                    Text(stringResource(R.string.bazarr_search_manual), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { open = null; languagesFor = w },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Translate, null)
                    Text(stringResource(R.string.bazarr_change_languages), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    languagesFor?.let { w ->
        LanguagesProfileDialog(api, w.radarrId, w.seriesId, onDismiss = { languagesFor = null }) {
            scope.launch { load(reset = true) }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.bazarr_search_all)) },
            text = { Text(stringResource(if (episodes) R.string.bazarr_search_all_episodes else R.string.bazarr_search_all_movies)) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; exec { if (episodes) api.searchAllEpisodes() else api.searchAllMovies() } }) {
                    Text(stringResource(R.string.bulk_search))
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SubHistoryTab(server: Server, api: BazarrApi) {
    var episodes by rememberSaveable { mutableStateOf(false) }
    var items by remember(episodes) { mutableStateOf<List<SubHistory>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    LaunchedEffect(episodes) {
        runCatching { if (episodes) api.episodeHistory() else api.movieHistory() }.onSuccess { items = it; error = null }.onFailure { error = it.toTestResult() }
    }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            SegmentedButton(selected = !episodes, onClick = { episodes = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.tab_movies)) }
            SegmentedButton(selected = episodes, onClick = { episodes = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.bazarr_episodes)) }
        }
        val l = items
        when {
            error != null && l == null -> ServerErrorCard(server, error!!)
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> MessageView(Icons.Outlined.SubtitlesOff, stringResource(R.string.history_empty))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(l.size) { i ->
                    val h = l[i]
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(h.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        h.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        h.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                        Text(
                            listOfNotNull(h.language, h.provider, h.score?.let { s -> stringResource(R.string.bazarr_score, s) }, h.time).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvidersTab(server: Server, api: BazarrApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SubProvider>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    suspend fun load() { runCatching { api.providers() }.onSuccess { items = it; error = null }.onFailure { error = it.toTestResult() } }
    LaunchedEffect(Unit) { load() }
    val l = items
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        when {
            error != null && l == null -> Column { ServerErrorCard(server, error!!) }
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> MessageView(Icons.Outlined.SubtitlesOff, stringResource(R.string.bazarr_no_providers))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                error?.let { e -> item { ServerErrorCard(server, e) } }
                val throttled = l.count { !it.working }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.bazarr_providers_summary, l.size - throttled, l.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (throttled > 0) {
                            TextButton(onClick = { confirmReset = true }) {
                                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.bazarr_reset_providers), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
                // Throttled providers first, so problems are on top.
                items(l.sortedBy { it.working }, key = { it.name }) { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.working) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.bazarr_provider_ok), tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Outlined.WarningAmber, stringResource(R.string.bazarr_provider_throttled), tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (p.working) stringResource(R.string.bazarr_provider_ok)
                                else listOfNotNull(p.status, p.retry?.let { stringResource(R.string.bazarr_retry, it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.working) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmReset) {
        val done = stringResource(R.string.bazarr_providers_reset_done)
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.bazarr_reset_providers_title)) },
            text = { Text(stringResource(R.string.bazarr_reset_providers_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        runCatching { api.resetProviders() }
                            .onSuccess { Toast.makeText(context, done, Toast.LENGTH_SHORT).show(); load() }
                            .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.bazarr_reset_providers)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Picks the languages profile for a movie ([radarrId]) or a whole series ([seriesId]).
 * Bazarr downloads, and searches for, the languages in that profile.
 */
@Composable
fun LanguagesProfileDialog(api: BazarrApi, radarrId: Int?, seriesId: Int?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<LanguagesProfile>?>(null) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var current by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            coroutineScope {
                val p = async { api.profiles() }
                val n = async { runCatching { api.languageNames() }.getOrDefault(emptyMap()) }
                val cur = async {
                    when {
                        radarrId != null -> api.movieProfile(radarrId)
                        seriesId != null -> api.seriesProfile(seriesId)
                        else -> null
                    }
                }
                Triple(p.await(), n.await(), cur.await())
            }
        }.onSuccess { (p, n, cur) -> profiles = p; names = n; current = cur; selected = cur }
            .onFailure { error = it.message ?: "" }
    }
    val saved = stringResource(R.string.bazarr_languages_saved)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bazarr_languages_title)) },
        text = {
            val p = profiles
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                p == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.selectableGroup()) {
                    if (radarrId == null) {
                        Text(stringResource(R.string.bazarr_languages_series_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (p.isEmpty()) {
                        Text(stringResource(R.string.bazarr_no_profiles), style = MaterialTheme.typography.bodyMedium)
                    }
                    val options: List<LanguagesProfile?> = p + listOf(null)
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(options.size) { i ->
                            val o = options[i]
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = selected == o?.id, role = Role.RadioButton) { selected = o?.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == o?.id, onClick = null)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(o?.name ?: stringResource(R.string.bazarr_no_profile), style = MaterialTheme.typography.bodyLarge)
                                    val detail = if (o == null) stringResource(R.string.bazarr_no_profile_body)
                                    else o.languages.joinToString(", ") { l ->
                                        (names[l.code] ?: l.code.uppercase()) +
                                            (if (l.hi) " " + context.getString(R.string.bazarr_hi_short) else "") +
                                            (if (l.forced) " " + context.getString(R.string.bazarr_forced_short) else "")
                                    }
                                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = profiles != null && !saving && selected != current,
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            when {
                                radarrId != null -> api.setMovieProfile(radarrId, selected)
                                seriesId != null -> api.setSeriesProfile(seriesId, selected)
                                else -> Unit
                            }
                        }.onSuccess {
                            Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                            onDismiss()
                            onChanged()
                        }.onFailure {
                            saving = false
                            Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Manual subtitle search for one movie or episode; tap a result to download it. */
@Composable
fun SubtitleSearchScreen(serverId: String, radarrId: Int?, seriesId: Int?, episodeId: Int?, title: String) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember(serverId) { c.servers.byId(serverId) }
    var results by remember { mutableStateOf<List<SubtitleResult>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var confirm by remember { mutableStateOf<SubtitleResult?>(null) }
    var done by remember { mutableStateOf<Set<String>>(emptySet()) }
    var run by remember { mutableIntStateOf(0) }
    var editLanguages by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(title) {
                if (server != null && (radarrId != null || seriesId != null)) {
                    IconButton(onClick = { editLanguages = true }) { Icon(Icons.Outlined.Translate, stringResource(R.string.bazarr_change_languages)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (server == null) {
                MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
                return@Box
            }
            val api = remember(server) { c.bazarr(server) }
            LaunchedEffect(run) {
                results = null
                error = null
                runCatching {
                    when {
                        radarrId != null -> api.movieSubtitles(radarrId)
                        episodeId != null -> api.episodeSubtitles(episodeId)
                        else -> emptyList()
                    }
                }.onSuccess { results = it }.onFailure { error = it.toTestResult() }
            }
            val r = results
            when {
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type))
                r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.bazarr_searching), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                r.isEmpty() -> MessageView(Icons.Outlined.SubtitlesOff, stringResource(R.string.bazarr_no_results))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    val languages = r.mapNotNull { it.language }.distinct().sorted()
                    if (languages.size > 1) {
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = language == null, onClick = { language = null }, label = { Text(stringResource(R.string.bazarr_all_languages)) })
                                languages.forEach { l -> FilterChip(selected = language == l, onClick = { language = if (language == l) null else l }, label = { Text(l) }) }
                            }
                        }
                    }
                    items(r.filter { language == null || it.language == language }, key = { it.provider + "|" + it.subtitle }) { s ->
                        Column(Modifier.fillMaxWidth().clickable { confirm = s }.padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.releaseInfo.firstOrNull() ?: s.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Chip(stringResource(R.string.bazarr_score_pct, s.score), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                Chip(s.provider)
                                s.language?.let { Chip(it) }
                                if (s.hi.equals("True", true)) Chip(stringResource(R.string.bazarr_hi))
                                if (s.forced.equals("True", true)) Chip(stringResource(R.string.bazarr_forced))
                                if (s.subtitle in done) Chip(stringResource(R.string.bazarr_downloaded), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                            }
                            if (s.matches.isNotEmpty()) Text(stringResource(R.string.bazarr_matches, s.matches.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s.dontMatches.isNotEmpty()) Text(stringResource(R.string.bazarr_dont_match, s.dontMatches.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (editLanguages) {
                LanguagesProfileDialog(api, radarrId, if (radarrId == null) seriesId else null, onDismiss = { editLanguages = false }) {
                    language = null
                    run++
                }
            }
            confirm?.let { s ->
                val ok = stringResource(R.string.bazarr_downloaded)
                AlertDialog(
                    onDismissRequest = { confirm = null },
                    title = { Text(stringResource(R.string.bazarr_download_title)) },
                    text = { Text(listOfNotNull(s.releaseInfo.firstOrNull(), s.provider, s.language).joinToString("\n"), style = MaterialTheme.typography.bodySmall) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = null
                            scope.launch {
                                runCatching {
                                    when {
                                        radarrId != null -> api.downloadMovieSubtitle(radarrId, s)
                                        seriesId != null && episodeId != null -> api.downloadEpisodeSubtitle(seriesId, episodeId, s)
                                        else -> Unit
                                    }
                                }
                                    .onSuccess { done = done + s.subtitle; Toast.makeText(context, ok, Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                            }
                        }) { Text(stringResource(R.string.bazarr_download)) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}
