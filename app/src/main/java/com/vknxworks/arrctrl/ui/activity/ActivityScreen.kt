package com.vknxworks.arrctrl.ui.activity

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.nav.AccountRoute
import com.vknxworks.arrctrl.ui.nav.AddTorrentRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class ActivityTab { QUEUE, TORRENTS, HISTORY }

@Composable
fun ActivityScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val hasArr = servers.any { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }
    val hasQbit = servers.any { it.type == ServiceType.QBITTORRENT }

    if (!hasArr && !hasQbit) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_activity))
            MessageView(
                Icons.Outlined.Download,
                stringResource(R.string.empty_activity_title),
                stringResource(R.string.empty_activity_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
                actionLabel = stringResource(R.string.add_server),
                onAction = { nav.navigate(AccountRoute) },
            )
        }
        return
    }

    val model: ActivityModel = viewModel { ActivityModel(c) }
    val tabs = buildList {
        if (hasArr) add(ActivityTab.QUEUE)
        if (hasQbit) add(ActivityTab.TORRENTS)
        if (hasArr) add(ActivityTab.HISTORY)
    }
    var tabName by rememberSaveable { mutableStateOf(tabs.first().name) }
    val tab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()

    // Poll only while the Activity tab is on screen and the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(servers, tab) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    if (hasQbit) model.refreshTorrents()
                    when (tab) {
                        ActivityTab.QUEUE -> model.refreshQueue()
                        ActivityTab.HISTORY -> if (!model.historyLoaded) model.loadHistory(reset = true)
                        ActivityTab.TORRENTS -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Per-server errors are kept by the model; anything else just waits for the next round.
                }
                delay(if (tab == ActivityTab.TORRENTS) 2_000 else 5_000)
            }
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_activity)) {
                val t = model.transfer
                if (hasQbit && t != null) {
                    val altOn = t.altSpeed
                    val toastOn = stringResource(R.string.alt_speed_on)
                    val toastOff = stringResource(R.string.alt_speed_off)
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { model.toggleAltSpeed() }
                                .onSuccess { Toast.makeText(context, if (altOn) toastOff else toastOn, Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_SHORT).show() }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Speed,
                            stringResource(if (altOn) R.string.alt_speed_turn_off else R.string.alt_speed_turn_on),
                            tint = if (altOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hasQbit) SpeedStrip(model)

            if (tabs.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
                    tabs.forEachIndexed { i, t ->
                        val count = when (t) {
                            ActivityTab.QUEUE -> model.queue.size.takeIf { model.queueLoaded }
                            ActivityTab.TORRENTS -> model.torrents.size.takeIf { model.torrentsLoaded }
                            ActivityTab.HISTORY -> null
                        }
                        val label = stringResource(
                            when (t) {
                                ActivityTab.QUEUE -> R.string.activity_queue
                                ActivityTab.TORRENTS -> R.string.activity_torrents
                                ActivityTab.HISTORY -> R.string.activity_history
                            },
                        )
                        SegmentedButton(
                            selected = t == tab,
                            onClick = { tabName = t.name },
                            shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                            icon = {},
                        ) { Text(if (count != null && count > 0) "$label $count" else label, maxLines = 1) }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        runCatching {
                            when (tab) {
                                ActivityTab.QUEUE -> model.refreshQueue()
                                ActivityTab.TORRENTS -> model.refreshTorrents()
                                ActivityTab.HISTORY -> model.loadHistory(reset = true)
                            }
                        }
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when (tab) {
                    ActivityTab.QUEUE -> QueueTab(model)
                    ActivityTab.TORRENTS -> TorrentsTab(model)
                    ActivityTab.HISTORY -> HistoryTab(model)
                }
            }
        }

        if (tab == ActivityTab.TORRENTS) {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(AddTorrentRoute()) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.torrent_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Gutter, bottom = LocalBottomClearance.current + 16.dp),
            )
        }
    }
}

/** Combined qBittorrent speeds, plus a note when alternative limits are on. */
@Composable
private fun SpeedStrip(model: ActivityModel) {
    val t = model.transfer ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.download_speed), Modifier.padding(end = 2.dp), tint = MaterialTheme.colorScheme.primary)
            Text(Format.speed(t.dlSpeed), style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.upload_speed), Modifier.padding(end = 2.dp), tint = MaterialTheme.colorScheme.tertiary)
            Text(Format.speed(t.upSpeed), style = MaterialTheme.typography.labelLarge)
        }
        if (t.altSpeed) {
            Text(
                stringResource(R.string.alt_speed_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Shared by the tabs: turns any failure into the same wording the connection test uses. */
@Composable
fun errorText(e: Throwable, type: ServiceType): String = resultText(e.toTestResult(), type)
