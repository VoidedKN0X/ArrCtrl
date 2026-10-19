package com.vknxworks.arrctrl.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R

/** What the library selection bar can do with the selected items. */
enum class SelectionAction { MONITOR, UNMONITOR, PROFILE, SEARCH, DELETE }

/**
 * Top bar while items are selected in a library. The count always stays on one line; the common actions are
 * buttons and the rest (select all, change quality profile) sit in the overflow menu, so it fits narrow phones.
 */
@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onAction: (SelectionAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.selection_clear)) }
        Text(
            stringResource(R.string.selection_count, count),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(onClick = { onAction(SelectionAction.MONITOR) }) { Icon(Icons.Outlined.BookmarkBorder, stringResource(R.string.bulk_monitor)) }
        IconButton(onClick = { onAction(SelectionAction.UNMONITOR) }) { Icon(Icons.Outlined.BookmarkRemove, stringResource(R.string.bulk_unmonitor)) }
        IconButton(onClick = { onAction(SelectionAction.SEARCH) }) { Icon(Icons.Outlined.Search, stringResource(R.string.bulk_search)) }
        IconButton(onClick = { onAction(SelectionAction.DELETE) }) {
            Icon(Icons.Outlined.Delete, stringResource(R.string.bulk_delete), tint = MaterialTheme.colorScheme.error)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.selection_all)) },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                    onClick = { menu = false; onSelectAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.bulk_profile)) },
                    leadingIcon = { Icon(Icons.Outlined.HighQuality, null) },
                    onClick = { menu = false; onAction(SelectionAction.PROFILE) },
                )
            }
        }
    }
}
