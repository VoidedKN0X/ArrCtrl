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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.BazarrApi
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
                Spacer(Modifier.size(8.dp))
            }
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
    var items by remember { mutableStateOf<List<SubProvider>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    LaunchedEffect(Unit) { runCatching { api.providers() }.onSuccess { items = it }.onFailure { error = it.toTestResult() } }
    val l = items
    when {
        error != null && l == null -> ServerErrorCard(server, error!!)
        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        l.isEmpty() -> MessageView(Icons.Outlined.CheckCircle, stringResource(R.string.bazarr_providers_ok), stringResource(R.string.bazarr_providers_ok_body))
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(l, key = { it.name }) { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(p.status, p.retry?.let { stringResource(R.string.bazarr_retry, it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (server == null) {
                MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
                return@Box
            }
            val api = remember(server) { c.bazarr(server) }
            LaunchedEffect(Unit) {
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
                    items(r, key = { it.provider + "|" + it.subtitle }) { s ->
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
