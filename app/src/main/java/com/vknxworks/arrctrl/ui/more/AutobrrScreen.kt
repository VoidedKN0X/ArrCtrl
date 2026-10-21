package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import com.vknxworks.arrctrl.ui.servers.failureText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.AutobrrApi
import com.vknxworks.arrctrl.data.api.AutobrrFilter
import com.vknxworks.arrctrl.data.api.AutobrrRelease
import com.vknxworks.arrctrl.data.api.AutobrrStats
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.IrcNetwork
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.series.relative
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun AutobrrScreen() = ToolScaffold(stringResource(R.string.service_autobrr), ServiceType.AUTOBRR) { server ->
    val c = LocalContext.current.container
    val api = remember(server) { c.autobrr(server) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<AutobrrStats?>(null) }
    LaunchedEffect(Unit) { runCatching { api.stats() }.onSuccess { stats = it } }
    Column(Modifier.fillMaxSize()) {
        stats?.let { s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.autobrr_seen), s.total.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.autobrr_approved), s.approved.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.autobrr_rejected), (s.rejected + s.filterRejected).toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.autobrr_errors), s.errors.toString(), Modifier.weight(1f), highlight = s.errors > 0)
            }
        }
        SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf(R.string.autobrr_releases, R.string.autobrr_filters, R.string.autobrr_irc).forEachIndexed { i, l ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(l)) })
            }
        }
        when (tab) {
            0 -> ReleasesTab(server, api)
            1 -> FiltersTab(server, api)
            else -> IrcTab(server, api)
        }
    }
}

@Composable
private fun outcomeLabel(o: String): String = stringResource(
    when (o) {
        "PUSH_APPROVED" -> R.string.autobrr_pushed
        "PUSH_REJECTED" -> R.string.autobrr_push_rejected
        "PUSH_ERROR" -> R.string.autobrr_push_error
        "FILTER_REJECTED" -> R.string.autobrr_filter_rejected
        "FILTER_APPROVED" -> R.string.autobrr_filter_approved
        else -> R.string.autobrr_pending
    },
)

@Composable
private fun outcomeColors(o: String): Pair<Color, Color> = when (o) {
    "PUSH_APPROVED", "FILTER_APPROVED" -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    "PUSH_ERROR" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    "PUSH_REJECTED", "FILTER_REJECTED" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ReleasesTab(server: Server, api: AutobrrApi) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AutobrrRelease>?>(null) }
    var total by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean) {
        val offset = if (reset) 0 else items.orEmpty().size
        runCatching { api.releases(offset) }
            .onSuccess { (page, t) -> items = if (reset) page else items.orEmpty() + page; total = t; error = null }
            .onFailure { error = it.toTestResult() }
    }
    LaunchedEffect(Unit) { load(true) }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(true); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        val l = items
        when {
            l == null && error != null -> Column { ServerErrorCard(server, error!!) }
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            l.isEmpty() -> MessageView(Icons.Outlined.Inbox, stringResource(R.string.autobrr_no_releases))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(l, key = { it.id }) { r ->
                    val (bg, fg) = outcomeColors(r.outcome)
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Chip(outcomeLabel(r.outcome), bg, fg)
                            r.filter?.let { Chip(it) }
                            r.actions.mapNotNull { it.client }.distinct().forEach { Chip(it) }
                        }
                        Text(
                            listOfNotNull(r.indexer, r.size.takeIf { it > 0 }?.let { Format.bytes(it) }, r.time?.let { relative(it.toEpochMilli()) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        (r.rejections + r.actions.flatMap { it.rejections }).distinct().take(3).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
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

@Composable
private fun FiltersTab(server: Server, api: AutobrrApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AutobrrFilter>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    suspend fun load() { runCatching { api.filters() }.onSuccess { items = it; error = null }.onFailure { error = it.toTestResult() } }
    LaunchedEffect(Unit) { load() }
    val l = items
    when {
        l == null && error != null -> ServerErrorCard(server, error!!)
        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        l.isEmpty() -> MessageView(Icons.Outlined.FilterAltOff, stringResource(R.string.autobrr_no_filters))
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(l, key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.prowlarr_priority, f.priority), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(f.enabled, onCheckedChange = { on ->
                        scope.launch {
                            runCatching { api.setFilterEnabled(f.id, on) }
                                .onSuccess { load() }
                                .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun IrcTab(server: Server, api: AutobrrApi) {
    var items by remember { mutableStateOf<List<IrcNetwork>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    LaunchedEffect(Unit) { runCatching { api.irc() }.onSuccess { items = it }.onFailure { error = it.toTestResult() } }
    val l = items
    when {
        l == null && error != null -> ServerErrorCard(server, error!!)
        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        l.isEmpty() -> MessageView(Icons.Outlined.LinkOff, stringResource(R.string.autobrr_no_irc))
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)) {
            items(l, key = { it.id }) { n ->
                val ok = n.enabled && n.connected && n.healthy
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            null,
                            Modifier.size(18.dp),
                            tint = when {
                                !n.enabled -> MaterialTheme.colorScheme.outline
                                ok -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(n.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(
                                when {
                                    !n.enabled -> R.string.prowlarr_disabled
                                    !n.connected -> R.string.autobrr_disconnected
                                    !n.healthy -> R.string.autobrr_unhealthy
                                    else -> R.string.status_connected
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOfNotNull("${n.server}:${n.port}", n.nick, n.connectedSince?.let { stringResource(R.string.autobrr_since, relative(it.toEpochMilli())) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    n.errors.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    n.channels.forEach { ch ->
                        Row(Modifier.padding(start = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                when {
                                    !ch.enabled -> stringResource(R.string.prowlarr_disabled)
                                    !ch.monitoring -> stringResource(R.string.autobrr_not_monitoring)
                                    ch.lastAnnounce != null -> stringResource(R.string.autobrr_last_announce, relative(ch.lastAnnounce.toEpochMilli()))
                                    else -> stringResource(R.string.autobrr_monitoring)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ch.enabled && !ch.monitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
