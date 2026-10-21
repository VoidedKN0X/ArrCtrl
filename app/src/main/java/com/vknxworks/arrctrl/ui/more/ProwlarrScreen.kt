package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import com.vknxworks.arrctrl.ui.servers.failureText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Indexer
import com.vknxworks.arrctrl.data.api.IndexerStat
import com.vknxworks.arrctrl.data.api.ProwlarrApi
import com.vknxworks.arrctrl.data.api.SearchResult
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.common.releaseAge
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.series.relative
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProwlarrScreen() = ToolScaffold(stringResource(R.string.service_prowlarr), ServiceType.PROWLARR) { server ->
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val c = LocalContext.current.container
    val api = remember(server) { c.prowlarr(server) }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf(R.string.prowlarr_indexers, R.string.search, R.string.prowlarr_stats).forEachIndexed { i, l ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(l)) })
            }
        }
        when (tab) {
            0 -> IndexersTab(server, api)
            1 -> SearchTab(server, api)
            else -> StatsTab(server, api)
        }
    }
}

@Composable
private fun IndexersTab(server: Server, api: ProwlarrApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Indexer>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Indexer?>(null) }

    suspend fun load() {
        runCatching { api.indexers() }.onSuccess { list = it; error = null }.onFailure { error = it.toTestResult() }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        val l = list
        when {
            l == null && error != null -> Column { ServerErrorCard(server, error!!) }
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> MessageView(Icons.Outlined.SearchOff, stringResource(R.string.prowlarr_no_indexers))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                error?.let { e -> item { ServerErrorCard(server, e) } }
                item {
                    Text(
                        stringResource(R.string.prowlarr_summary, l.count { it.enabled }, l.size, l.count { it.disabledTill != null }),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                    )
                }
                items(l, key = { it.id }) { i ->
                    Row(
                        Modifier.fillMaxWidth().clickable { open = i }.padding(start = Gutter, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(i.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                i.protocol?.let { Chip(it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }) }
                                i.privacy?.let { Chip(privacyLabel(it)) }
                                Chip(stringResource(R.string.prowlarr_priority, i.priority))
                                when {
                                    !i.enabled -> Chip(stringResource(R.string.prowlarr_disabled))
                                    i.disabledTill != null -> Chip(stringResource(R.string.prowlarr_failing), MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                                }
                            }
                            i.disabledTill?.let {
                                Text(stringResource(R.string.prowlarr_paused_until, relative(it.toEpochMilli())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Switch(i.enabled, onCheckedChange = { on ->
                            scope.launch {
                                runCatching { api.setEnabled(i, on) }
                                    .onSuccess { load() }
                                    .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                            }
                        })
                    }
                }
            }
        }
    }

    open?.let { i -> IndexerSheet(api, i, onDismiss = { open = null }) }
}

@Composable
private fun privacyLabel(p: String): String = stringResource(
    when (p) {
        "private" -> R.string.prowlarr_private
        "semiPrivate" -> R.string.prowlarr_semi_private
        else -> R.string.prowlarr_public
    },
)

@Composable
private fun IndexerSheet(api: ProwlarrApi, i: Indexer, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val passed = stringResource(R.string.prowlarr_test_passed)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(i.name, style = MaterialTheme.typography.titleLarge)
            i.definition?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            i.failingSince?.let { Text(stringResource(R.string.prowlarr_failing_since, relative(it.toEpochMilli())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            FilledTonalButton(
                onClick = {
                    testing = true; result = null
                    scope.launch {
                        result = runCatching { api.test(i) ?: passed }.getOrElse { failureText(context, it) }
                        testing = false
                    }
                },
                enabled = !testing,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (testing) R.string.status_checking else R.string.prowlarr_test)) }
            result?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it == passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SearchTab(server: Server, api: ProwlarrApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var confirm by remember { mutableStateOf<SearchResult?>(null) }
    var sent by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun search() {
        if (query.isBlank()) return
        focus.clearFocus(); searching = true; error = null
        scope.launch {
            runCatching { api.search(query) }.onSuccess { results = it.sortedByDescending { r -> r.seeders ?: -1 } }.onFailure { error = it.toTestResult() }
            searching = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.prowlarr_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
        )
        val r = results
        when {
            searching -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> ServerErrorCard(server, error!!)
            r == null -> MessageView(Icons.Outlined.Search, stringResource(R.string.prowlarr_search_intro), stringResource(R.string.prowlarr_search_intro_body))
            r.isEmpty() -> MessageView(Icons.Outlined.SearchOff, stringResource(R.string.release_none))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(r, key = { it.guid + "|" + it.indexerId }) { s ->
                    Column(
                        Modifier.fillMaxWidth().clickable { confirm = s }.padding(horizontal = Gutter, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            s.categories.take(2).forEach { Chip(it) }
                            if (s.guid in sent) Chip(stringResource(R.string.release_sent_chip), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            listOfNotNull(
                                s.indexer,
                                Format.bytes(s.size),
                                releaseAge(s.ageHours),
                                if (s.protocol == "torrent") stringResource(R.string.release_peers, s.seeders ?: 0, s.leechers ?: 0) else s.protocol,
                                s.grabs?.let { stringResource(R.string.prowlarr_grabs, it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val sentText = stringResource(R.string.prowlarr_sent)
    confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.prowlarr_grab_title)) },
            text = { Text(s.title + "\n\n" + stringResource(R.string.prowlarr_grab_body), style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        runCatching { api.grab(s) }
                            .onSuccess { sent = sent + s.guid; Toast.makeText(context, sentText, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.prowlarr_grab)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatsTab(server: Server, api: ProwlarrApi) {
    var stats by remember { mutableStateOf<List<IndexerStat>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    LaunchedEffect(Unit) { runCatching { api.stats() }.onSuccess { stats = it }.onFailure { error = it.toTestResult() } }
    val s = stats
    when {
        error != null -> ServerErrorCard(server, error!!)
        s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        s.isEmpty() -> MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.prowlarr_no_stats))
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.prowlarr_queries), s.sumOf { it.queries }.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.prowlarr_grabs_label), s.sumOf { it.grabs }.toString(), Modifier.weight(1f))
                    val failed = s.sumOf { it.failedQueries + it.failedGrabs }
                    StatTile(stringResource(R.string.prowlarr_failures), failed.toString(), Modifier.weight(1f), highlight = failed > 0)
                }
            }
            items(s.sortedByDescending { it.queries }, key = { it.indexerId }) { st ->
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(st.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.prowlarr_stat_line, st.queries, st.grabs, st.failedQueries + st.failedGrabs, st.avgResponseMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (st.failedQueries + st.failedGrabs > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
