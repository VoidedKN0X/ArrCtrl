package com.vknxworks.arrctrl.ui.activity

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Peer
import com.vknxworks.arrctrl.data.api.Torrent
import com.vknxworks.arrctrl.data.api.TorrentFile
import com.vknxworks.arrctrl.data.api.TorrentProperties
import com.vknxworks.arrctrl.data.api.Tracker
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.ui.components.ChoiceSheet
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private enum class DetailTab(val label: Int) {
    INFO(R.string.torrent_tab_info), FILES(R.string.torrent_tab_files), TRACKERS(R.string.torrent_tab_trackers), PEERS(R.string.torrent_tab_peers)
}

@Composable
fun TorrentScreen(serverId: String, hash: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("")
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.qbit(server) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var torrent by remember { mutableStateOf<Torrent?>(null) }
    var gone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var props by remember { mutableStateOf<TorrentProperties?>(null) }
    var files by remember { mutableStateOf<List<TorrentFile>?>(null) }
    var trackers by remember { mutableStateOf<List<Tracker>?>(null) }
    var peers by remember { mutableStateOf<List<Peer>?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = DetailTab.entries[tabIndex]
    var showActions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(server) { categories = runCatching { api.categories() }.getOrDefault(emptyList()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tab, refreshTick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val t = api.info(hash)
                    if (t == null) gone = true else torrent = t
                    when (tab) {
                        DetailTab.INFO -> props = api.properties(hash)
                        DetailTab.FILES -> files = api.files(hash)
                        DetailTab.TRACKERS -> trackers = api.trackers(hash)
                        DetailTab.PEERS -> peers = api.peers(hash)
                    }
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = e.toTestResult()
                }
                delay(2_000)
            }
        }
    }

    val t = torrent
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(t?.name.orEmpty()) {
                if (t != null) {
                    val actions = rememberTorrentActions(t, context, scope) { refreshTick++ }
                    if (t.kind.isStopped) {
                        IconButton(onClick = { actions.start() }) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.torrent_resume)) }
                    } else {
                        IconButton(onClick = { actions.stop() }) { Icon(Icons.Outlined.Pause, stringResource(R.string.torrent_pause)) }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.torrent_delete)) }
                    IconButton(onClick = { showActions = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_actions)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                gone -> {
                    MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.torrent_gone))
                    return@Column
                }
                t == null && error != null -> {
                    MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type))
                    return@Column
                }
                t == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
            }
            val cur = t!!
            Column(Modifier.padding(horizontal = Gutter, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { cur.progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    listOfNotNull(torrentStateText(cur), Format.percent(cur.progress), Format.bytes(cur.size), Format.eta(cur.eta)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(
                        R.string.torrent_speeds_and,
                        Format.speed(cur.dlSpeed),
                        Format.speed(cur.upSpeed),
                        stringResource(R.string.torrent_ratio_peers, Format.ratio(cur.ratio), cur.seeds, cur.seedsTotal, cur.peers, cur.peersTotal),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(resultText(it, server.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            SecondaryTabRow(selectedTabIndex = tabIndex, containerColor = MaterialTheme.colorScheme.background) {
                DetailTab.entries.forEachIndexed { i, d ->
                    Tab(selected = i == tabIndex, onClick = { tabIndex = i }, text = { Text(stringResource(d.label)) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DetailTab.INFO -> InfoTab(cur, props)
                    DetailTab.FILES -> FilesTab(files) { indexes, prio ->
                        scope.launch {
                            runCatching { api.setFilePriority(hash, indexes, prio) }
                            files = runCatching { api.files(hash) }.getOrNull() ?: files
                        }
                    }
                    DetailTab.TRACKERS -> TrackersTab(trackers)
                    DetailTab.PEERS -> PeersTab(peers)
                }
            }
        }
    }

    if (showActions && t != null) {
        TorrentActionsSheet(t, categories, onDismiss = { showActions = false }, onChanged = { refreshTick++ }, onDeleted = { nav.back() })
    }
    if (confirmDelete && t != null) {
        DeleteTorrentDialog(t, onDismiss = { confirmDelete = false }, onDeleted = { confirmDelete = false; nav.back() })
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun InfoTab(t: Torrent, p: TorrentProperties?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Gutter, vertical = 12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.navigationBarsPadding()) {
                Detail(R.string.detail_server, t.server.name)
                Detail(R.string.detail_category, t.category.ifBlank { "–" })
                if (t.tags.isNotEmpty()) Detail(R.string.detail_tags, t.tags.joinToString(", "))
                Detail(R.string.detail_path, p?.savePath ?: t.savePath ?: "–")
                p?.downloaded?.let { Detail(R.string.detail_downloaded, Format.bytes(it)) }
                p?.uploaded?.let { Detail(R.string.detail_uploaded, Format.bytes(it)) }
                Detail(R.string.detail_ratio, Format.ratio(p?.shareRatio ?: t.ratio))
                Detail(R.string.detail_limit_down, if (t.dlLimit > 0) Format.speed(t.dlLimit) else stringResource(R.string.torrent_limit_unlimited))
                Detail(R.string.detail_limit_up, if (t.upLimit > 0) Format.speed(t.upLimit) else stringResource(R.string.torrent_limit_unlimited))
                if (t.priority > 0) Detail(R.string.detail_queue_position, t.priority.toString())
                p?.timeElapsed?.let { Detail(R.string.detail_active, Format.eta(it) ?: "–") }
                p?.seedingTime?.takeIf { it > 0 }?.let { Detail(R.string.detail_seeding_time, Format.eta(it) ?: "–") }
                (p?.addedOn ?: t.addedOn.takeIf { it > 0 })?.let { Detail(R.string.detail_added, date(it)) }
                p?.completionOn?.let { Detail(R.string.detail_completed, date(it)) }
                if (p?.piecesNum != null && p.pieceSize != null) {
                    Detail(R.string.detail_pieces, "${p.piecesHave ?: 0} / ${p.piecesNum} × ${Format.bytes(p.pieceSize)}")
                }
                p?.comment?.let { Detail(R.string.detail_comment, it) }
                Detail(R.string.detail_hash, t.hash)
            }
        }
    }
}

private fun date(epochSeconds: Long): String =
    DateUtils.getRelativeTimeSpanString(epochSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun FilesTab(files: List<TorrentFile>?, onPriority: (List<Int>, Int) -> Unit) {
    if (files == null) { Loading(); return }
    var picking by remember { mutableStateOf<TorrentFile?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(files, key = { it.index }) { f ->
            Column(
                Modifier.fillMaxWidth().clickable { picking = f }.padding(horizontal = Gutter, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { f.progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    listOf(Format.percent(f.progress), Format.bytes(f.size), stringResource(filePriorityLabel(f.priority))).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (f.priority == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    picking?.let { f ->
        ChoiceSheet(
            stringResource(R.string.file_priority),
            listOf(0, 1, 6, 7).map { it to stringResource(filePriorityLabel(it)) },
            selected = f.priority,
            onDismiss = { picking = null },
        ) { p ->
            picking = null
            onPriority(listOf(f.index), p)
        }
    }
}

private fun filePriorityLabel(p: Int): Int = when (p) {
    0 -> R.string.file_priority_skip
    6 -> R.string.file_priority_high
    7 -> R.string.file_priority_max
    else -> R.string.file_priority_normal
}

@Composable
private fun TrackersTab(trackers: List<Tracker>?) {
    if (trackers == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(trackers, key = { it.url }) { tr ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr.url, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = stringResource(
                    when (tr.status) {
                        0 -> R.string.tracker_disabled
                        1 -> R.string.tracker_not_contacted
                        2 -> R.string.tracker_working
                        3 -> R.string.tracker_updating
                        else -> R.string.tracker_not_working
                    },
                )
                Text(
                    if (tr.url.startsWith("** [")) status else
                        listOf(status, stringResource(R.string.tracker_counts, tr.seeds.coerceAtLeast(0), tr.leeches.coerceAtLeast(0), tr.peers.coerceAtLeast(0))).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tr.status == 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tr.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PeersTab(peers: List<Peer>?) {
    if (peers == null) { Loading(); return }
    if (peers.isEmpty()) { MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.peers_none)); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(peers, key = { it.address }) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.address, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        listOfNotNull(p.client, p.country, Format.percent(p.progress)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.torrent_speeds_lines, Format.speed(p.dlSpeed), Format.speed(p.upSpeed)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
