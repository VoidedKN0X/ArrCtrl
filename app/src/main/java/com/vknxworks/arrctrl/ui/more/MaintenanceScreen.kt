package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.ArrSystemApi
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.ServerBackup
import com.vknxworks.arrctrl.data.api.ServerLogEntry
import com.vknxworks.arrctrl.data.api.ServerUpdate
import com.vknxworks.arrctrl.data.api.ServerVersion
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.CtrlAlertDialog
import com.vknxworks.arrctrl.ui.components.CtrlFilterChip
import com.vknxworks.arrctrl.ui.components.CtrlPrimaryButton
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.series.relative
import com.vknxworks.arrctrl.ui.servers.failureText
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.CtrlType
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MaintenanceTab(val label: Int) {
    BACKUPS(R.string.maint_backups),
    UPDATES(R.string.maint_updates),
    LOGS(R.string.maint_logs),
}

/**
 * Backups, updates and logs of one Radarr, Sonarr, Lidarr or Prowlarr server: what the server's own
 * System pages show. A chip per server, then three tabs.
 */
@Composable
fun MaintenanceScreen() {
    val c = LocalContext.current.container
    val all by c.servers.servers.collectAsStateWithLifecycle()
    val servers = all.filter { it.type in ArrSystemApi.TYPES }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(MaintenanceTab.BACKUPS) }
    val server = servers.firstOrNull { it.id == chosen } ?: servers.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.maint_title))
        if (server == null) {
            MessageView(Icons.Outlined.CloudOff, stringResource(R.string.maint_no_servers), stringResource(R.string.maint_no_servers_body))
            return@Column
        }
        if (servers.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s -> CtrlFilterChip(selected = s.id == server.id, onClick = { chosen = s.id }, label = { Text(s.name) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaintenanceTab.entries.forEach { t -> CtrlFilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(stringResource(t.label)) }) }
        }
        key(server.id, tab) {
            when (tab) {
                MaintenanceTab.BACKUPS -> BackupsTab(server)
                MaintenanceTab.UPDATES -> UpdatesTab(server)
                MaintenanceTab.LOGS -> LogsTab(server)
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
    )
}

// ---------- Backups ----------

@Composable
private fun BackupsTab(server: Server) {
    val context = LocalContext.current
    val c = context.container
    val api = remember(server) { ArrSystemApi(c.http, server) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<ServerBackup>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ServerBackup?>(null) }

    suspend fun load() {
        try {
            list = api.backups()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            error?.let { e -> item { ServerErrorCard(server, e) } }
            item {
                CtrlPrimaryButton(
                    stringResource(if (working) R.string.maint_backup_starting else R.string.maint_backup_now),
                    onClick = {
                        if (working) return@CtrlPrimaryButton
                        working = true
                        scope.launch {
                            try {
                                api.backupNow()
                                Toast.makeText(context, R.string.maint_backup_started, Toast.LENGTH_SHORT).show()
                                // The server makes the backup in the background; look again when it has had a moment.
                                delay(4000)
                                load()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Toast.makeText(context, failureText(context, e), Toast.LENGTH_LONG).show()
                            } finally {
                                working = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
            val backups = list
            when {
                backups == null && error == null -> item { Loading() }
                backups != null && backups.isEmpty() -> item { Note(stringResource(R.string.maint_backups_none)) }
                backups != null -> items(backups, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = CtrlType.Mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    stringResource(
                                        when (b.type) {
                                            "manual" -> R.string.maint_backup_manual
                                            "update" -> R.string.maint_backup_update
                                            else -> R.string.maint_backup_scheduled
                                        },
                                    ),
                                    Format.bytes(b.size),
                                    b.time?.let { relative(it.toEpochMilli()) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deleting = b }) { Icon(Icons.Outlined.Delete, stringResource(R.string.maint_backup_delete)) }
                    }
                }
            }
            item { Note(stringResource(R.string.maint_backups_note)) }
        }
    }

    deleting?.let { b ->
        CtrlAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.maint_backup_delete_title)) },
            text = { Text(stringResource(R.string.maint_backup_delete_body, b.name, server.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            api.deleteBackup(b.id)
                            load()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Toast.makeText(context, failureText(context, e), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.maint_backup_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            shielded = true,
        )
    }
}

// ---------- Updates ----------

@Composable
private fun UpdatesTab(server: Server) {
    val context = LocalContext.current
    val c = context.container
    val api = remember(server) { ArrSystemApi(c.http, server) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf<ServerVersion?>(null) }
    var list by remember { mutableStateOf<List<ServerUpdate>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            version = api.version()
            list = api.updates()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            error?.let { e -> item { ServerErrorCard(server, e) } }
            val v = version
            val updates = list
            if (v == null && updates == null && error == null) item { Loading() }
            if (v != null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.maint_running, v.version ?: "?"), style = MaterialTheme.typography.titleMedium)
                        v.branch?.let { Text(stringResource(R.string.maint_branch, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (v != null && updates != null) {
                // The same rule the server's own Updates page uses for its "Install latest" button.
                val pending = updates.firstOrNull { it.installable && it.latest && !it.installed }
                when {
                    pending == null -> item { Note(stringResource(if (updates.any { it.installed && it.latest }) R.string.maint_up_to_date else R.string.maint_no_update)) }
                    v.canInstall -> item {
                        CtrlPrimaryButton(
                            stringResource(if (started) R.string.maint_update_started else R.string.maint_update_install, pending.version),
                            onClick = { if (!started) confirm = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                        )
                    }
                    else -> item {
                        Note(
                            stringResource(if (v.isDocker || v.updateMechanism == "docker") R.string.maint_update_docker else R.string.maint_update_external, pending.version) +
                                (v.updateMessage?.let { "\n$it" } ?: ""),
                        )
                    }
                }
                items(updates, key = { it.version }) { u ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(u.version, style = MaterialTheme.typography.titleMedium)
                            if (u.installed) Chip(stringResource(R.string.maint_installed))
                            else if (u.latest) Chip(stringResource(R.string.maint_latest), color = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
                        }
                        u.releaseDate?.let {
                            Text(relative(it.toEpochMilli()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (u.added.isNotEmpty()) {
                            Text(stringResource(R.string.maint_changes_new), style = CtrlType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            u.added.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (u.fixed.isNotEmpty()) {
                            Text(stringResource(R.string.maint_changes_fixed), style = CtrlType.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            u.fixed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (u.added.isEmpty() && u.fixed.isEmpty()) {
                            Text(stringResource(R.string.maint_changes_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        val pending = list?.firstOrNull { it.installable && it.latest && !it.installed }
        CtrlAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.maint_update_title, server.name)) },
            text = { Text(stringResource(R.string.maint_update_body, pending?.version ?: "?", server.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        try {
                            api.installUpdate()
                            started = true
                            Toast.makeText(context, R.string.maint_update_toast, Toast.LENGTH_LONG).show()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Toast.makeText(context, failureText(context, e), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.maint_update_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
            shielded = true,
        )
    }
}

// ---------- Logs ----------

private val LOG_LEVELS = listOf(null to R.string.maint_log_all, "info" to R.string.maint_log_info, "warn" to R.string.maint_log_warn, "error" to R.string.maint_log_error)

@Composable
private fun LogsTab(server: Server) {
    val c = LocalContext.current.container
    val api = remember(server) { ArrSystemApi(c.http, server) }
    val scope = rememberCoroutineScope()
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<ServerLogEntry>?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ServerLogEntry?>(null) }

    suspend fun load() {
        try {
            val (list, t) = api.logs(1, level)
            entries = list
            total = t
            page = 1
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        }
    }
    LaunchedEffect(level) {
        entries = null
        load()
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOG_LEVELS.forEach { (value, label) ->
                        CtrlFilterChip(selected = level == value, onClick = { level = value }, label = { Text(stringResource(label)) })
                    }
                }
            }
            error?.let { e -> item { ServerErrorCard(server, e) } }
            val list = entries
            when {
                list == null && error == null -> item { Loading() }
                list != null && list.isEmpty() -> item { Note(stringResource(R.string.maint_logs_none)) }
                list != null -> {
                    items(list, key = { it.id }) { e ->
                        val bad = e.level == "error" || e.level == "fatal"
                        val warn = e.level == "warn"
                        Column(Modifier.fillMaxWidth().clickable { open = e }.padding(horizontal = Gutter, vertical = 6.dp)) {
                            Text(
                                e.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(e.level.uppercase(), e.logger, e.time?.let { relative(it.toEpochMilli()) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (warn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (list.size < total) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(
                                    onClick = {
                                        if (loadingMore) return@OutlinedButton
                                        loadingMore = true
                                        scope.launch {
                                            runCatching { api.logs(page + 1, level) }.onSuccess { (more, t) ->
                                                val seen = list.map { it.id }.toSet()
                                                entries = list + more.filter { it.id !in seen }
                                                total = t
                                                page += 1
                                            }
                                            loadingMore = false
                                        }
                                    },
                                    shape = CtrlShapes.Button,
                                ) { Text(stringResource(R.string.history_load_more)) }
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { e ->
        CtrlAlertDialog(
            onDismissRequest = { open = null },
            icon = { Icon(Icons.Outlined.Info, null) },
            title = { Text(listOfNotNull(e.level.uppercase(), e.logger).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.time?.let { Text(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withZone(java.time.ZoneId.systemDefault()).format(it)) }
                        Text(e.message, color = MaterialTheme.colorScheme.onSurface)
                        e.exceptionType?.let { Text(it, style = CtrlType.Mono) }
                        e.exception?.let { Text(it, style = CtrlType.Mono) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
