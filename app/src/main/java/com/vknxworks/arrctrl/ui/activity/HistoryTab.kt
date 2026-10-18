package com.vknxworks.arrctrl.ui.activity

import android.text.format.DateUtils
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.api.HistoryItem
import com.vknxworks.arrctrl.data.api.HistoryKind
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private enum class HistoryFilter(val label: Int, val kinds: Set<HistoryKind>?) {
    ALL(R.string.filter_all, null),
    GRABBED(R.string.history_grabbed, setOf(HistoryKind.GRABBED)),
    IMPORTED(R.string.history_imported, setOf(HistoryKind.IMPORTED)),
    FAILED(R.string.history_failed, setOf(HistoryKind.FAILED)),
    DELETED(R.string.history_deleted, setOf(HistoryKind.DELETED)),
}

@Composable
fun HistoryTab(model: ActivityModel) {
    val scope = rememberCoroutineScope()
    var filterName by rememberSaveable { mutableStateOf(HistoryFilter.ALL.name) }
    val filter = HistoryFilter.valueOf(filterName)
    val shown = model.history.filter { filter.kinds == null || it.kind in filter.kinds }
    val multiServer = model.arrServers.size > 1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current)) {
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { filterName = f.name }, label = { Text(stringResource(f.label)) })
                }
            }
        }
        items(model.historyErrors.entries.toList(), key = { "err-" + it.key.id }) { (s, r) -> ServerErrorCard(s, r) }
        if (!model.historyLoaded) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (shown.isEmpty() && model.historyEnd) {
            item(key = "empty") { MessageView(Icons.Outlined.History, stringResource(R.string.history_empty)) }
        }
        items(shown, key = { it.key }) { h -> HistoryRow(h, multiServer) }
        if (model.historyLoaded && !model.historyEnd) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (model.historyLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(onClick = { scope.launch { runCatching { model.loadHistory(reset = false) } } }, shape = CircleShape) {
                            Text(stringResource(R.string.history_load_more))
                        }
                    }
                }
            }
        }
    }
}

private fun HistoryKind.icon(): ImageVector = when (this) {
    HistoryKind.GRABBED -> Icons.Outlined.CloudDownload
    HistoryKind.IMPORTED -> Icons.Outlined.TaskAlt
    HistoryKind.FAILED -> Icons.Outlined.ErrorOutline
    HistoryKind.DELETED -> Icons.Outlined.DeleteOutline
    HistoryKind.RENAMED -> Icons.Outlined.DriveFileRenameOutline
    HistoryKind.IGNORED -> Icons.Outlined.Block
    HistoryKind.OTHER -> Icons.Outlined.Info
}

@Composable
private fun HistoryKind.label(): String = stringResource(
    when (this) {
        HistoryKind.GRABBED -> R.string.history_grabbed
        HistoryKind.IMPORTED -> R.string.history_imported
        HistoryKind.FAILED -> R.string.history_failed
        HistoryKind.DELETED -> R.string.history_deleted
        HistoryKind.RENAMED -> R.string.history_renamed
        HistoryKind.IGNORED -> R.string.history_ignored
        HistoryKind.OTHER -> R.string.history_other
    },
)

@Composable
private fun HistoryRow(h: HistoryItem, showServer: Boolean) {
    val bad = h.kind == HistoryKind.FAILED
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            h.kind.icon(),
            h.kind.label(),
            Modifier.padding(top = 2.dp).size(20.dp),
            tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val when_ = h.date?.let {
                DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            }
            Text(
                listOfNotNull(h.kind.label(), h.detail, when_, if (showServer) h.server.name else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            h.release?.takeIf { it != h.title }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (bad) h.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3) }
            h.quality?.let { Chip(it) }
        }
    }
}
