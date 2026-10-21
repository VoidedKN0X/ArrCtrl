package com.vknxworks.arrctrl.ui.activity

import android.widget.Toast
import com.vknxworks.arrctrl.ui.servers.failureText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.QueueItem
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ManualImportRoute
import com.vknxworks.arrctrl.ui.servers.icon
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun QueueTab(model: ActivityModel) {
    var open by remember { mutableStateOf<QueueItem?>(null) }
    val multiServer = model.arrServers.size > 1

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current),
    ) {
        items(model.queueErrors.entries.toList(), key = { "err-" + it.key.id }) { (s, r) -> ServerErrorCard(s, r) }
        if (model.queueLoaded && model.queue.isEmpty() && model.queueErrors.isEmpty()) {
            item(key = "empty") {
                MessageView(Icons.Outlined.CheckCircle, stringResource(R.string.queue_empty_title), stringResource(R.string.queue_empty_body))
            }
        }
        items(model.queue, key = { it.key }) { q -> QueueRow(q, multiServer) { open = q } }
    }

    open?.let { q ->
        // Show the latest copy of the item while the sheet is open.
        val live = model.queue.firstOrNull { it.key == q.key } ?: q
        QueueSheet(live, model, onDismiss = { open = null })
    }
}

@Composable
private fun QueueRow(q: QueueItem, showServer: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(q.server.type.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(q.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (q.hasWarning) Icon(Icons.Outlined.WarningAmber, stringResource(R.string.queue_needs_attention), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
        val sub = listOfNotNull(q.detail, if (showServer) q.server.name else null).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LinearProgressIndicator(
            progress = { q.progress },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            color = if (q.hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            q.quality?.let { Chip(it) }
            Text(
                listOfNotNull(
                    queueStatusText(q),
                    Format.bytes(q.size),
                    Format.eta(q.etaSeconds)?.let { stringResource(R.string.eta_left, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (q.hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        q.messages.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun queueStatusText(q: QueueItem): String = stringResource(
    when {
        q.trackedState == "importBlocked" -> R.string.queue_import_blocked
        q.trackedState == "importPending" -> R.string.queue_import_pending
        q.trackedState == "importing" -> R.string.queue_importing
        q.trackedState == "failedPending" || q.status == "failed" -> R.string.queue_failed
        q.status == "paused" -> R.string.queue_paused
        q.status == "queued" -> R.string.queue_queued
        q.status == "delay" -> R.string.queue_delayed
        q.status == "completed" -> R.string.queue_completed
        q.status == "downloadClientUnavailable" -> R.string.queue_client_unavailable
        q.status == "warning" -> R.string.queue_warning
        else -> R.string.queue_downloading
    },
)

@Composable
private fun QueueSheet(q: QueueItem, model: ActivityModel, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(q.title, style = MaterialTheme.typography.titleLarge)
            q.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(q.release, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { q.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Detail(R.string.detail_status, queueStatusText(q))
            Detail(R.string.detail_progress, "${Format.bytes(q.size - q.sizeLeft)} / ${Format.bytes(q.size)}")
            Format.eta(q.etaSeconds)?.let { Detail(R.string.detail_eta, it) }
            q.quality?.let { Detail(R.string.detail_quality, it) }
            q.client?.let { Detail(R.string.detail_client, it) }
            q.indexer?.let { Detail(R.string.detail_indexer, it) }
            q.protocol?.let { Detail(R.string.detail_protocol, it) }
            Detail(R.string.detail_server, q.server.name)
            q.outputPath?.let { Detail(R.string.detail_path, it) }
            (q.messages + listOfNotNull(q.errorMessage)).distinct().forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.size(4.dp))
            if (q.canManualImport) {
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        nav.navigate(ManualImportRoute(q.server.id, q.downloadId.orEmpty(), q.title))
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.DriveFileMove, null)
                    Text(stringResource(R.string.manual_import), Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = { confirmRemove = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, null)
                Text(stringResource(R.string.queue_remove), Modifier.padding(start = 8.dp))
            }
        }
    }

    if (confirmRemove) {
        var fromClient by remember { mutableStateOf(true) }
        var blocklist by remember { mutableStateOf(false) }
        var skipSearch by remember { mutableStateOf(false) }
        val done = stringResource(R.string.queue_removed)
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.queue_remove_title, q.title)) },
            text = {
                Column {
                    CheckRow(stringResource(R.string.queue_remove_from_client), fromClient) { fromClient = it }
                    CheckRow(stringResource(R.string.queue_blocklist), blocklist) { blocklist = it }
                    if (blocklist) CheckRow(stringResource(R.string.queue_skip_search), skipSearch) { skipSearch = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        runCatching { c.arr(q.server).removeFromQueue(q.id, fromClient, blocklist, skipSearch) }
                            .onSuccess {
                                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                                onDismiss()
                                runCatching { model.refreshQueue() }
                            }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun Detail(label: Int, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
