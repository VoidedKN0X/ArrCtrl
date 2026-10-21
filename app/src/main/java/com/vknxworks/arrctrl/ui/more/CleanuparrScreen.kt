package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import com.vknxworks.arrctrl.ui.servers.failureText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vknxworks.arrctrl.data.api.CleanupEvent
import com.vknxworks.arrctrl.data.api.CleanupJob
import com.vknxworks.arrctrl.data.api.CleanupStats
import com.vknxworks.arrctrl.data.api.ClientHealth
import com.vknxworks.arrctrl.data.api.Strike
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.series.relative
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** "QueueCleaner" → "Queue cleaner"; Cleanuparr's enum names are the only labels it gives. */
fun humanize(name: String): String =
    name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun CleanuparrScreen() = ToolScaffold(stringResource(R.string.service_cleanuparr), ServiceType.CLEANUPARR) { server ->
    val c = LocalContext.current.container
    val context = LocalContext.current
    val api = remember(server) { c.cleanuparr(server) }
    val scope = rememberCoroutineScope()
    var hours by rememberSaveable { mutableIntStateOf(24) }
    var stats by remember { mutableStateOf<CleanupStats?>(null) }
    var jobs by remember { mutableStateOf<List<CleanupJob>>(emptyList()) }
    var strikes by remember { mutableStateOf<List<Strike>>(emptyList()) }
    var events by remember { mutableStateOf<List<CleanupEvent>>(emptyList()) }
    var eventsTotal by remember { mutableIntStateOf(0) }
    var eventPage by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf<CleanupJob?>(null) }

    suspend fun load() = coroutineScope {
        val s = async { runCatching { api.stats(hours) } }
        val j = async { runCatching { api.jobs() } }
        val st = async { runCatching { api.recentStrikes(20) } }
        val ev = async { runCatching { api.events(1) } }
        s.await().onSuccess { stats = it; error = null }.onFailure { error = it.toTestResult() }
        j.await().onSuccess { jobs = it }
        st.await().onSuccess { strikes = it }
        ev.await().onSuccess { (list, t) -> events = list; eventsTotal = t; eventPage = 1 }
    }
    LaunchedEffect(hours) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            error?.let { e -> item { ServerErrorCard(server, e) } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24 to R.string.cleanuparr_24h, 168 to R.string.cleanuparr_7d, 720 to R.string.cleanuparr_30d).forEach { (h, l) ->
                        FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(stringResource(l)) })
                    }
                }
            }
            val s = stats
            if (s == null && error == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            if (s != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stringResource(R.string.cleanuparr_removed), s.removals.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.cleanuparr_strikes), s.strikes.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.cleanuparr_recovered), s.recovered.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.cleanuparr_job_runs), "${s.jobsCompleted}/${s.jobRuns}", Modifier.weight(1f), highlight = s.jobsFailed > 0)
                    }
                }
                // Removals by reason, then strikes by type (marked ⚠).
                val breakdown = s.removalsByReason.filterValues { it > 0 }.map { (k, v) -> "${humanize(k)} $v" } +
                    s.strikesByType.filterValues { it > 0 }.map { (k, v) -> "⚠ ${humanize(k)} $v" }
                if (breakdown.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            breakdown.forEach { Chip(it) }
                        }
                    }
                }
                val clients = s.downloadClients + s.arrInstances
                if (clients.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.cleanuparr_connections)) }
                    items(clients, key = { "c-" + it.name + it.type }) { h -> HealthRow(h) }
                }
            }
            if (jobs.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.cleanuparr_jobs)) }
                items(jobs, key = { "j-" + it.jobType }) { j ->
                    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(humanize(j.name), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(
                                    j.status.ifBlank { null },
                                    j.previous?.let { stringResource(R.string.cleanuparr_last_run, relative(it.toEpochMilli())) },
                                    j.next?.let { stringResource(R.string.cleanuparr_next_run, relative(it.toEpochMilli())) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (j.canTrigger) IconButton(onClick = { trigger = j }) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.cleanuparr_run_now)) }
                    }
                }
            }
            if (strikes.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.cleanuparr_recent_strikes)) }
                items(strikes, key = { "s-" + it.id }) { st ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
                        Text(st.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(humanize(st.type), st.time?.let { relative(it.toEpochMilli()) }, if (st.dryRun) stringResource(R.string.cleanuparr_dry_run) else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (events.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.cleanuparr_events)) }
                items(events, key = { "e-" + it.id }) { e ->
                    val bad = e.severity.equals("Error", true) || e.severity.equals("Warning", true)
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
                        Text(e.message, style = MaterialTheme.typography.bodyMedium, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 3)
                        Text(
                            listOfNotNull(humanize(e.type), e.severity, e.time?.let { relative(it.toEpochMilli()) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (events.size < eventsTotal) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { api.events(eventPage + 1) }.onSuccess { (list, t) -> events = events + list; eventsTotal = t; eventPage += 1 }
                                }
                            }, shape = CircleShape) { Text(stringResource(R.string.history_load_more)) }
                        }
                    }
                }
            }
        }
    }

    trigger?.let { j ->
        val started = stringResource(R.string.cleanuparr_started)
        AlertDialog(
            onDismissRequest = { trigger = null },
            title = { Text(stringResource(R.string.cleanuparr_run_title, humanize(j.name))) },
            text = { Text(stringResource(R.string.cleanuparr_run_body)) },
            confirmButton = {
                TextButton(onClick = {
                    trigger = null
                    scope.launch {
                        runCatching { api.trigger(j.jobType) }
                            .onSuccess { Toast.makeText(context, started, Toast.LENGTH_SHORT).show(); load() }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.cleanuparr_run_now)) }
            },
            dismissButton = { TextButton(onClick = { trigger = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun HealthRow(h: ClientHealth) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (h.healthy) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            null,
            Modifier.size(18.dp),
            tint = if (h.healthy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(h.name, h.type).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            h.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
