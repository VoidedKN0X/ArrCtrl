package com.vknxworks.arrctrl.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.notify.NotifyChecker
import com.vknxworks.arrctrl.data.notify.NotifyEvent
import com.vknxworks.arrctrl.data.notify.NotifySchedule
import com.vknxworks.arrctrl.data.notify.Notifications
import com.vknxworks.arrctrl.data.settings.AppSettings
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.servers.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.unifiedpush.android.connector.UnifiedPush
import java.util.concurrent.TimeUnit

private val INTERVALS = listOf(15, 30, 60, 180, 0)

/** "Every 30 min · instant with UnifiedPush", or "Off": the summary on the Account screen. */
@Composable
fun notificationsSummary(s: AppSettings): String = when {
    !s.notifyEnabled -> stringResource(R.string.notify_off)
    else -> listOfNotNull(
        if (s.notifyInterval > 0) intervalLabel(s.notifyInterval) else null,
        if (s.pushEnabled && s.pushEndpoint != null) stringResource(R.string.notify_summary_push) else null,
    ).joinToString(" · ").ifBlank { stringResource(R.string.notify_on) }
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.notify_interval_never)
    minutes < 60 -> stringResource(R.string.notify_interval_minutes, minutes)
    else -> intervalHours(minutes / 60)
}

@Composable
private fun intervalHours(hours: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.notify_interval_hours, hours, hours)

private fun ignoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun copy(context: Context, label: String, text: String) {
    // The push address works like a password (anyone with it can wake ArrCtrl), so keep it out of clipboard previews.
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = android.os.PersistableBundle().apply { putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

/** Splits a push endpoint into the server and topic that services with an ntfy option ask for. */
private fun ntfyParts(endpoint: String): Pair<String, String>? {
    val u = runCatching { Uri.parse(endpoint) }.getOrNull() ?: return null
    val topic = u.lastPathSegment ?: return null
    val host = u.host ?: return null
    val port = if (u.port > 0) ":${u.port}" else ""
    return "${u.scheme}://$host$port" to topic
}

@Composable
fun NotificationsScreen() {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    var allowed by remember { mutableStateOf(Notifications.allowed(context)) }
    var battery by remember { mutableStateOf(ignoringBattery(context)) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = Notifications.allowed(context)
        battery = ignoringBattery(context)
        tick++
    }

    fun turnOn() {
        // Start from a clean slate so turning notifications on doesn't report everything that already happened.
        NotifyChecker.reset(context)
        update { it.copy(notifyEnabled = true) }
        NotifySchedule.checkNow(context, push = false)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = Notifications.allowed(context)
        if (granted) turnOn()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.notifications)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(footer = stringResource(R.string.notify_intro)) {
                GroupSwitch(
                    stringResource(R.string.notifications),
                    if (s.notifyEnabled && !allowed) stringResource(R.string.notify_blocked) else notificationsSummary(s),
                    s.notifyEnabled,
                    if (s.notifyEnabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                ) { on ->
                    when {
                        !on -> update { it.copy(notifyEnabled = false) }
                        !allowed -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> turnOn()
                    }
                }
                if (s.notifyEnabled && !allowed) {
                    GroupRow(
                        stringResource(R.string.notify_open_system),
                        stringResource(R.string.notify_open_system_body),
                        Icons.Outlined.Settings,
                        iconTint = MaterialTheme.colorScheme.error,
                        showChevron = true,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                    )
                }
            }

            if (s.notifyEnabled) {
                Group(stringResource(R.string.notify_how), footer = stringResource(R.string.notify_how_footer)) {
                    GroupChoice(
                        stringResource(R.string.notify_interval),
                        INTERVALS.map { it to intervalLabel(it) },
                        s.notifyInterval,
                        Icons.Outlined.Schedule,
                    ) { v -> update { it.copy(notifyInterval = v) } }
                    PushRows(s, ::update)
                    val lastCheck = remember(tick, s) { NotifyChecker.lastCheck(context) }
                    val lastPush = remember(tick, s) { NotifyChecker.lastPush(context) }
                    GroupRow(
                        stringResource(R.string.notify_check_now),
                        listOfNotNull(
                            lastCheck.takeIf { it > 0 }?.let { stringResource(R.string.notify_last_check, DateUtils.getRelativeTimeSpanString(it).toString()) },
                            lastPush.takeIf { it > 0 && s.pushEnabled }?.let { stringResource(R.string.notify_last_push, DateUtils.getRelativeTimeSpanString(it).toString()) },
                        ).joinToString(" · ").ifBlank { null },
                        Icons.Outlined.Refresh,
                        onClick = {
                            NotifySchedule.checkNow(context, push = false)
                            Toast.makeText(context, context.getString(R.string.notify_checking), Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                Group(stringResource(R.string.notify_events), footer = stringResource(R.string.notify_events_footer)) {
                    NotifyEvent.entries.forEach { e ->
                        GroupSwitch(stringResource(e.label), stringResource(e.description), e.name in s.notifyEvents) { on ->
                            update { it.copy(notifyEvents = if (on) it.notifyEvents + e.name else it.notifyEvents - e.name) }
                        }
                    }
                }

                if (servers.isNotEmpty()) {
                    Group(stringResource(R.string.servers), footer = stringResource(R.string.notify_servers_footer)) {
                        servers.forEach { srv ->
                            GroupSwitch(srv.name, stringResource(srv.type.label), srv.id !in s.notifyMuted, srv.type.icon) { on ->
                                update { it.copy(notifyMuted = if (on) it.notifyMuted - srv.id else it.notifyMuted + srv.id) }
                            }
                        }
                    }
                }

                Group(footer = stringResource(R.string.notify_ongoing_footer)) {
                    GroupSwitch(stringResource(R.string.notify_ongoing), stringResource(R.string.notify_ongoing_body), s.notifyOngoing, Icons.Outlined.Downloading) { on ->
                        update { it.copy(notifyOngoing = on) }
                    }
                }

                Group(stringResource(R.string.notify_battery), footer = stringResource(R.string.notify_battery_footer)) {
                    GroupRow(
                        stringResource(if (battery) R.string.notify_battery_ok else R.string.notify_battery_limited),
                        stringResource(if (battery) R.string.notify_battery_ok_body else R.string.notify_battery_limited_body),
                        if (battery) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                        iconTint = if (battery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        showChevron = !battery,
                        onClick = if (battery) null else ({
                            val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(ask) }
                                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
                        }),
                    )
                    GroupRow(
                        stringResource(R.string.notify_app_info),
                        stringResource(R.string.notify_app_info_body),
                        Icons.Outlined.Settings,
                        showChevron = true,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        },
                    )
                }
            }
        }
    }
}

/** The UnifiedPush switch and, once registered, the address to give your servers. */
@Composable
private fun PushRows(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distributors = remember(s.pushEnabled) { UnifiedPush.getDistributors(context) }
    GroupRow(
        stringResource(R.string.notify_push),
        when {
            distributors.isEmpty() && !s.pushEnabled -> stringResource(R.string.notify_push_none)
            s.pushEnabled && s.pushEndpoint == null -> stringResource(R.string.notify_push_waiting)
            s.pushEnabled -> stringResource(R.string.notify_push_on)
            else -> stringResource(R.string.notify_push_body)
        },
        Icons.Outlined.Bolt,
        enabled = distributors.isNotEmpty() || s.pushEnabled,
        onClick = null,
        trailing = {
            Switch(
                checked = s.pushEnabled,
                enabled = distributors.isNotEmpty() || s.pushEnabled,
                onCheckedChange = { on ->
                    if (on) {
                        // Uses the distributor you picked before, or your phone's default one (asking if there are several).
                        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { ok ->
                            if (ok) {
                                update { it.copy(pushEnabled = true) }
                                UnifiedPush.register(context, messageForDistributor = context.getString(R.string.app_name))
                            } else {
                                Toast.makeText(context, context.getString(R.string.notify_push_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        UnifiedPush.unregister(context)
                        update { it.copy(pushEnabled = false, pushEndpoint = null) }
                    }
                },
            )
        },
    )
    val endpoint = s.pushEndpoint
    if (s.pushEnabled && endpoint != null) {
        val share = stringResource(R.string.notify_push_share_title)
        GroupRow(
            stringResource(R.string.notify_push_address),
            endpoint,
            Icons.Outlined.ContentCopy,
            onClick = { copy(context, "ArrCtrl", endpoint) },
            trailing = {
                IconButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, endpoint), share))
                }) { Icon(Icons.Outlined.Share, share) }
            },
        )
        ntfyParts(endpoint)?.let { (server, topic) ->
            GroupRow(stringResource(R.string.notify_push_server), server, onClick = { copy(context, "ArrCtrl", server) })
            GroupRow(stringResource(R.string.notify_push_topic), topic, onClick = { copy(context, "ArrCtrl", topic) })
        }
        GroupRow(stringResource(R.string.notify_push_test), stringResource(R.string.notify_push_test_body), Icons.Outlined.Send, onClick = {
            scope.launch {
                // Posts to your own push address only (no keys or server details), exactly as your servers will.
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
                        client.newCall(Request.Builder().url(endpoint).post("ArrCtrl test".toRequestBody("text/plain".toMediaType())).build()).execute().use { it.isSuccessful }
                    }.getOrDefault(false)
                }
                Toast.makeText(context, context.getString(if (ok) R.string.notify_push_test_sent else R.string.notify_push_test_failed), Toast.LENGTH_LONG).show()
            }
        })
    }
}
