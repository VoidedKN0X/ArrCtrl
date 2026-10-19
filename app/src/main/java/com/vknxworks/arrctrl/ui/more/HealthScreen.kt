package com.vknxworks.arrctrl.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.DiskSpace
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.arrDiskSpace
import com.vknxworks.arrctrl.data.api.arrHealth
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.ConnectionTest
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.servers.icon
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class Severity { ERROR, WARNING, NOTICE }

/** One problem from any service, in a common shape. */
data class Problem(val severity: Severity, val message: String, val source: String? = null, val wikiUrl: String? = null)

/** What one server reported: its problems and (Radarr and Sonarr) its disks. [failure] when it couldn't be reached. */
data class ServerHealth(val server: Server, val problems: List<Problem>, val disks: List<DiskSpace>, val failure: TestResult?)

private fun severity(type: String) = when (type.lowercase()) {
    "error" -> Severity.ERROR
    "warning" -> Severity.WARNING
    else -> Severity.NOTICE
}

/** Asks every configured server about its health, all at once. */
suspend fun checkHealth(c: AppContainer, servers: List<Server>): List<ServerHealth> = coroutineScope {
    servers.map { s -> async { checkOne(c, s) } }.awaitAll()
}

private suspend fun checkOne(c: AppContainer, s: Server): ServerHealth = try {
    when (s.type) {
        ServiceType.RADARR, ServiceType.SONARR, ServiceType.PROWLARR -> coroutineScope {
            val issues = async { arrHealth(c.http, s) }
            val disks = async { if (s.type == ServiceType.PROWLARR) emptyList() else runCatching { arrDiskSpace(c.http, s) }.getOrDefault(emptyList()) }
            ServerHealth(
                s,
                issues.await().filter { it.type != "ok" }.map { Problem(severity(it.type), it.message, it.source, it.wikiUrl) },
                disks.await(),
                null,
            )
        }
        ServiceType.BAZARR -> ServerHealth(s, c.bazarr(s).health().map { Problem(Severity.WARNING, it.issue, it.obj) }, emptyList(), null)
        ServiceType.AUTOBRR -> {
            val problems = c.autobrr(s).irc().filter { it.enabled }.flatMap { n ->
                buildList {
                    if (!n.connected) add(Problem(Severity.ERROR, c.context.getString(R.string.health_irc_disconnected, n.name)))
                    else if (!n.healthy) add(Problem(Severity.WARNING, c.context.getString(R.string.health_irc_unhealthy, n.name)))
                    n.channels.filter { it.enabled && !it.monitoring }.forEach { ch ->
                        add(Problem(Severity.WARNING, c.context.getString(R.string.health_irc_channel, ch.name, n.name)))
                    }
                }
            }
            ServerHealth(s, problems, emptyList(), null)
        }
        ServiceType.CLEANUPARR -> ServerHealth(
            s,
            c.cleanuparr(s).clientHealth().filter { !it.healthy }.map { Problem(Severity.ERROR, it.error ?: c.context.getString(R.string.health_client_down), it.name) },
            emptyList(),
            null,
        )
        ServiceType.QBITTORRENT -> {
            val r = ConnectionTest.run(c.http, s, draft = false)
            ServerHealth(s, emptyList(), emptyList(), r.takeIf { it !is TestResult.Ok })
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ServerHealth(s, emptyList(), emptyList(), e.toTestResult())
}

@Composable
fun HealthScreen() {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val threshold = settings.diskWarnPercent
    var results by remember { mutableStateOf<List<ServerHealth>?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(servers) { results = checkHealth(c, servers) }

    fun lowDisks(h: ServerHealth) = h.disks.filter { it.total > 0 && it.freeFraction * 100 < threshold }
    val problemCount = results?.sumOf { h -> h.problems.count { it.severity != Severity.NOTICE } + lowDisks(h).size + (if (h.failure != null) 1 else 0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.system_health)) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; results = checkHealth(c, servers); refreshing = false } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val list = results
            when {
                servers.isEmpty() -> MessageView(Icons.Outlined.Info, stringResource(R.string.health_no_servers))
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (problemCount == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (problemCount == 0) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                    null,
                                    tint = if (problemCount == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (problemCount == 0) stringResource(R.string.health_all_good)
                                    else androidx.compose.ui.res.pluralStringResource(R.plurals.health_problems, problemCount ?: 0, problemCount ?: 0),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                    item {
                        Group {
                            GroupChoice(
                                stringResource(R.string.health_disk_threshold),
                                listOf(5, 10, 15, 20, 25).map { it to stringResource(R.string.health_disk_threshold_value, it) },
                                threshold,
                                Icons.Outlined.Storage,
                            ) { v -> scope.launch { c.settings.update { it.copy(diskWarnPercent = v) } } }
                        }
                    }
                    items(list, key = { it.server.id }) { h ->
                        val low = lowDisks(h)
                        val ok = h.failure == null && h.problems.none { it.severity != Severity.NOTICE } && low.isEmpty()
                        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(h.server.type.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(h.server.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Icon(
                                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    null,
                                    tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                )
                            }
                            h.failure?.let { Text(resultText(it, h.server.type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                            if (h.failure == null && h.problems.isEmpty() && h.disks.isEmpty()) {
                                Text(stringResource(R.string.health_no_issues), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            h.problems.sortedBy { it.severity }.forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().then(
                                        // Only real web links: the address comes from the server, so no other schemes or apps.
                                        if (p.wikiUrl != null && Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(p.wikiUrl)) Modifier.clickable {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.wikiUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        } else Modifier,
                                    ),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        when (p.severity) {
                                            Severity.ERROR -> Icons.Outlined.ErrorOutline
                                            Severity.WARNING -> Icons.Outlined.WarningAmber
                                            Severity.NOTICE -> Icons.Outlined.Info
                                        },
                                        null,
                                        Modifier.size(16.dp).padding(top = 2.dp),
                                        tint = when (p.severity) {
                                            Severity.ERROR -> MaterialTheme.colorScheme.error
                                            Severity.WARNING -> MaterialTheme.colorScheme.primary
                                            Severity.NOTICE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        listOfNotNull(p.message, p.source?.let { "($it)" }).joinToString(" "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            h.disks.forEach { d ->
                                val usedPct = 1f - d.freeFraction.toFloat()
                                val isLow = d in low
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row {
                                        Text(d.label?.takeIf { it.isNotBlank() } ?: d.path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            stringResource(R.string.health_disk_free, Format.bytes(d.free), Format.bytes(d.total)),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { usedPct.coerceIn(0f, 1f) },
                                        color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    )
                                    if (isLow) Text(stringResource(R.string.health_disk_low, threshold), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
