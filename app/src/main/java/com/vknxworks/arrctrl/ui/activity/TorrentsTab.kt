package com.vknxworks.arrctrl.ui.activity

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Torrent
import com.vknxworks.arrctrl.data.api.TorrentKind
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.TorrentRoute
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private enum class TorrentFilter { ALL, DOWNLOADING, SEEDING, STOPPED, ERROR }

private fun TorrentFilter.matches(t: Torrent): Boolean = when (this) {
    TorrentFilter.ALL -> true
    TorrentFilter.DOWNLOADING -> t.kind == TorrentKind.DOWNLOADING || (t.kind == TorrentKind.STALLED && t.progress < 1.0) ||
        (t.kind == TorrentKind.QUEUED && t.progress < 1.0) || t.kind == TorrentKind.CHECKING
    TorrentFilter.SEEDING -> t.kind == TorrentKind.SEEDING || (t.progress >= 1.0 && (t.kind == TorrentKind.STALLED || t.kind == TorrentKind.QUEUED))
    TorrentFilter.STOPPED -> t.kind == TorrentKind.STOPPED
    TorrentFilter.ERROR -> t.kind == TorrentKind.ERROR
}

@Composable
fun TorrentsTab(model: ActivityModel) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var filterName by rememberSaveable { mutableStateOf(TorrentFilter.ALL.name) }
    val filter = TorrentFilter.valueOf(filterName)
    var actionsFor by remember { mutableStateOf<Torrent?>(null) }
    val multiServer = model.qbitServers.size > 1
    val shown = model.torrents.filter { filter.matches(it) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp + LocalBottomClearance.current)) {
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TorrentFilter.entries.forEach { f ->
                    val count = model.torrents.count { f.matches(it) }
                    FilterChip(
                        selected = f == filter,
                        onClick = { filterName = f.name },
                        label = { Text(stringResource(R.string.label_count, stringResource(f.label), count)) },
                    )
                }
            }
        }
        items(model.torrentErrors.entries.toList(), key = { "err-" + it.key.id }) { (s, r) -> ServerErrorCard(s, r) }
        if (model.torrentsLoaded && shown.isEmpty() && model.torrentErrors.isEmpty()) {
            item(key = "empty") {
                MessageView(Icons.Outlined.Inbox, stringResource(R.string.torrents_empty_title), stringResource(R.string.torrents_empty_body))
            }
        }
        items(shown, key = { it.key }) { t ->
            TorrentRow(
                t,
                showServer = multiServer,
                onClick = { nav.navigate(TorrentRoute(t.server.id, t.hash)) },
                onLongClick = { actionsFor = t },
            )
        }
    }

    actionsFor?.let { t ->
        val live = model.torrents.firstOrNull { it.key == t.key } ?: t
        TorrentActionsSheet(
            live,
            categories = model.categories[live.server.id].orEmpty(),
            onDismiss = { actionsFor = null },
            onChanged = { scope.launch { runCatching { model.refreshTorrents() } } },
        )
    }
}

private val TorrentFilter.label: Int
    get() = when (this) {
        TorrentFilter.ALL -> R.string.filter_all
        TorrentFilter.DOWNLOADING -> R.string.filter_downloading
        TorrentFilter.SEEDING -> R.string.filter_seeding
        TorrentFilter.STOPPED -> R.string.filter_stopped
        TorrentFilter.ERROR -> R.string.filter_error
    }

@Composable
fun torrentStateText(t: Torrent): String = stringResource(
    when (t.kind) {
        TorrentKind.DOWNLOADING -> if (t.state.startsWith("meta") || t.state == "forcedMetaDL") R.string.state_metadata else R.string.state_downloading
        TorrentKind.SEEDING -> R.string.state_seeding
        TorrentKind.STOPPED -> if (t.progress >= 1.0) R.string.state_completed else R.string.state_stopped
        TorrentKind.QUEUED -> R.string.state_queued
        TorrentKind.CHECKING -> if (t.state == "moving") R.string.state_moving else R.string.state_checking
        TorrentKind.STALLED -> R.string.state_stalled
        TorrentKind.ERROR -> if (t.state == "missingFiles") R.string.state_missing_files else R.string.state_error
        TorrentKind.OTHER -> R.string.state_unknown
    },
) + if (t.forceStart) " · " + stringResource(R.string.state_forced) else ""

@Composable
private fun TorrentRow(t: Torrent, showServer: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val error = t.kind == TorrentKind.ERROR
        LinearProgressIndicator(
            progress = { t.progress.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            color = when {
                error -> MaterialTheme.colorScheme.error
                t.kind == TorrentKind.STOPPED -> MaterialTheme.colorScheme.outline
                t.progress >= 1.0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Text(
            listOfNotNull(
                torrentStateText(t),
                Format.percent(t.progress),
                Format.bytes(t.size),
                if (t.progress < 1.0) Format.eta(t.eta) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    R.string.torrent_speeds_and,
                    Format.speed(t.dlSpeed),
                    Format.speed(t.upSpeed),
                    stringResource(R.string.torrent_ratio_peers, Format.ratio(t.ratio), t.seeds, t.seedsTotal, t.peers, t.peersTotal),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (t.category.isNotBlank() || t.tags.isNotEmpty() || showServer) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showServer) Chip(t.server.name)
                if (t.category.isNotBlank()) Chip(t.category, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                t.tags.take(3).forEach { Chip(it) }
            }
        }
    }
}
