package com.vknxworks.arrctrl.ui.activity

import android.content.Context
import com.vknxworks.arrctrl.ui.servers.failureText
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.Torrent
import com.vknxworks.arrctrl.ui.components.ChoiceSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which follow-up dialog an action opened. */
private enum class Dialog { DELETE, CATEGORY, TAGS, LIMITS, QUEUE }

/**
 * Every action on one torrent, as a bottom sheet. [onChanged] runs after an action succeeds
 * (to refresh the list); [onDeleted] after the torrent was removed.
 */
@Composable
fun TorrentActionsSheet(
    t: Torrent,
    categories: List<String>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit = onChanged,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    if (dialog == null) {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                val actions = rememberTorrentActions(t, context, scope, onChanged)
                if (t.kind.isStopped) {
                    ActionRow(Icons.Outlined.PlayArrow, stringResource(R.string.torrent_resume)) { actions.start(); onDismiss() }
                } else {
                    ActionRow(Icons.Outlined.Pause, stringResource(R.string.torrent_pause)) { actions.stop(); onDismiss() }
                }
                ActionRow(
                    Icons.Outlined.Bolt,
                    stringResource(if (t.forceStart) R.string.torrent_force_start_off else R.string.torrent_force_start),
                ) { actions.force(!t.forceStart); onDismiss() }
                ActionRow(Icons.Outlined.FactCheck, stringResource(R.string.torrent_recheck)) { actions.recheck(); onDismiss() }
                ActionRow(Icons.Outlined.Category, stringResource(R.string.torrent_category)) { dialog = Dialog.CATEGORY }
                ActionRow(Icons.Outlined.Label, stringResource(R.string.torrent_tags)) { dialog = Dialog.TAGS }
                ActionRow(Icons.Outlined.SwapVert, stringResource(R.string.torrent_queue_position)) { dialog = Dialog.QUEUE }
                ActionRow(Icons.Outlined.Speed, stringResource(R.string.torrent_speed_limits)) { dialog = Dialog.LIMITS }
                ActionRow(Icons.Outlined.Delete, stringResource(R.string.torrent_delete), danger = true) { dialog = Dialog.DELETE }
            }
        }
    }

    val close = { dialog = null; onDismiss() }
    when (dialog) {
        Dialog.DELETE -> DeleteTorrentDialog(t, onDismiss = close, onDeleted = { close(); onDeleted() })
        Dialog.CATEGORY -> CategoryPicker(t, categories, onDismiss = close, onChanged = onChanged)
        Dialog.TAGS -> TagsDialog(t, onDismiss = close, onChanged = onChanged)
        Dialog.LIMITS -> SpeedLimitDialog(t, onDismiss = close, onChanged = onChanged)
        Dialog.QUEUE -> QueuePositionSheet(t, onDismiss = close, onChanged = onChanged)
        null -> Unit
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** Simple actions that run straight away and report failures as a toast. */
class TorrentActions(private val t: Torrent, private val context: Context, private val scope: CoroutineScope, private val onChanged: () -> Unit) {
    private val api: QbitApi get() = context.container.qbit(t.server)

    fun run(block: suspend QbitApi.() -> Unit) {
        scope.launch {
            runCatching { api.block() }
                .onSuccess { onChanged() }
                .onFailure { e ->
                    val msg = if (e is com.vknxworks.arrctrl.data.api.ApiException && e.code == 409) {
                        context.getString(R.string.torrent_queueing_off)
                    } else failureText(context, e)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
        }
    }

    fun start() = run { start(listOf(t.hash)) }
    fun stop() = run { stop(listOf(t.hash)) }
    fun force(on: Boolean) = run { forceStart(listOf(t.hash), on) }
    fun recheck() = run { recheck(listOf(t.hash)) }
}

@Composable
fun rememberTorrentActions(t: Torrent, context: Context, scope: CoroutineScope, onChanged: () -> Unit): TorrentActions =
    remember(t, scope) { TorrentActions(t, context, scope, onChanged) }

@Composable
fun DeleteTorrentDialog(t: Torrent, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withFiles by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.torrent_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.name, style = MaterialTheme.typography.bodyMedium)
                CheckRow(stringResource(R.string.torrent_delete_files), withFiles) { withFiles = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                rememberlessRun(context, scope, onDeleted) { context.container.qbit(t.server).delete(listOf(t.hash), withFiles) }
            }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CategoryPicker(t: Torrent, categories: List<String>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val none = stringResource(R.string.torrent_category_none)
    ChoiceSheet(
        stringResource(R.string.torrent_category),
        listOf("" to none) + categories.map { it to it },
        selected = t.category,
        onDismiss = onDismiss,
    ) { cat ->
        rememberlessRun(context, scope, { onChanged(); onDismiss() }) { context.container.qbit(t.server).setCategory(listOf(t.hash), cat) }
    }
}

@Composable
private fun TagsDialog(t: Torrent, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(t.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.torrent_tags)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.torrent_tags_field)) },
                supportingText = { Text(stringResource(R.string.torrent_tags_help)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val wanted = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val add = wanted - t.tags.toSet()
                val remove = t.tags - wanted.toSet()
                rememberlessRun(context, scope, { onChanged(); onDismiss() }) {
                    val api = context.container.qbit(t.server)
                    if (remove.isNotEmpty()) api.removeTags(listOf(t.hash), remove)
                    if (add.isNotEmpty()) api.addTags(listOf(t.hash), add)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Limits are typed in KB/s; 0 or empty = unlimited. */
@Composable
private fun SpeedLimitDialog(t: Torrent, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun kb(v: Long) = if (v <= 0) "" else (v / 1024).toString()
    var down by remember { mutableStateOf(kb(t.dlLimit)) }
    var up by remember { mutableStateOf(kb(t.upLimit)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.torrent_speed_limits)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = down,
                    onValueChange = { v -> down = v.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.torrent_limit_down)) },
                    placeholder = { Text(stringResource(R.string.torrent_limit_unlimited)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = up,
                    onValueChange = { v -> up = v.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.torrent_limit_up)) },
                    placeholder = { Text(stringResource(R.string.torrent_limit_unlimited)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = (down.toLongOrNull() ?: 0) * 1024
                val u = (up.toLongOrNull() ?: 0) * 1024
                rememberlessRun(context, scope, { onChanged(); onDismiss() }) {
                    val api = context.container.qbit(t.server)
                    api.setDownloadLimit(listOf(t.hash), d)
                    api.setUploadLimit(listOf(t.hash), u)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun QueuePositionSheet(t: Torrent, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(stringResource(R.string.torrent_queue_position), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            fun go(m: QbitApi.Move) = rememberlessRun(context, scope, { onChanged(); onDismiss() }) { context.container.qbit(t.server).move(listOf(t.hash), m) }
            ActionRow(Icons.Outlined.KeyboardDoubleArrowUp, stringResource(R.string.torrent_queue_top)) { go(QbitApi.Move.TOP) }
            ActionRow(Icons.Outlined.KeyboardDoubleArrowUp, stringResource(R.string.torrent_queue_up)) { go(QbitApi.Move.UP) }
            ActionRow(Icons.Outlined.KeyboardDoubleArrowDown, stringResource(R.string.torrent_queue_down)) { go(QbitApi.Move.DOWN) }
            ActionRow(Icons.Outlined.KeyboardDoubleArrowDown, stringResource(R.string.torrent_queue_bottom)) { go(QbitApi.Move.BOTTOM) }
            Text(
                stringResource(R.string.torrent_queue_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

/** Runs a qBittorrent call from a click handler; toast on failure, [then] on success. */
private fun rememberlessRun(context: Context, scope: CoroutineScope, then: () -> Unit, block: suspend () -> Unit) {
    scope.launch {
        runCatching { block() }
            .onSuccess { then() }
            .onFailure { e ->
                val msg = if (e is com.vknxworks.arrctrl.data.api.ApiException && e.code == 409) {
                    context.getString(R.string.torrent_queueing_off)
                } else failureText(context, e)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
    }
}
