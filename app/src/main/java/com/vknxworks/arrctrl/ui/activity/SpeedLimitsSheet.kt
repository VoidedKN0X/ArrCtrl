package com.vknxworks.arrctrl.ui.activity

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.SpeedLimits
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.ui.components.CtrlFilterChip
import com.vknxworks.arrctrl.ui.components.CtrlLabel
import com.vknxworks.arrctrl.ui.components.CtrlPrimaryButton
import com.vknxworks.arrctrl.ui.components.CtrlSheet
import com.vknxworks.arrctrl.ui.components.CtrlTextField
import com.vknxworks.arrctrl.ui.servers.failureText
import com.vknxworks.arrctrl.ui.servers.resultText
import kotlinx.coroutines.launch

/**
 * qBittorrent's global speed limits: the normal download and upload limits and the alternative ones (used while
 * "Alternative speed limits" is on). Typed in KB/s; empty or 0 means unlimited. One server at a time.
 */
@Composable
fun SpeedLimitsSheet(servers: List<Server>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var chosen by remember { mutableStateOf(servers.firstOrNull()?.id) }
    val server = servers.firstOrNull { it.id == chosen } ?: servers.firstOrNull() ?: return
    CtrlSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.speed_limits_title), style = MaterialTheme.typography.titleLarge)
            if (servers.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    servers.forEach { s -> CtrlFilterChip(selected = s.id == server.id, onClick = { chosen = s.id }, label = { Text(s.name) }) }
                }
            }
            key(server.id) { LimitsForm(server, onSaved) }
        }
    }
}

@Composable
private fun LimitsForm(server: Server, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(server) { context.container.qbit(server) }
    var loaded by remember { mutableStateOf<Pair<SpeedLimits, Boolean>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var down by remember { mutableStateOf("") }
    var up by remember { mutableStateOf("") }
    var altDown by remember { mutableStateOf("") }
    var altUp by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val savedText = stringResource(R.string.speed_limits_saved)

    fun kb(v: Long) = if (v <= 0) "" else (v / 1024).toString()
    LaunchedEffect(server.id) {
        runCatching { api.speedLimits() to api.altSpeedActive() }
            .onSuccess { (l, alt) ->
                loaded = l to alt
                down = kb(l.download); up = kb(l.upload); altDown = kb(l.altDownload); altUp = kb(l.altUpload)
            }
            .onFailure { error = resultText(context, it.toTestResult(), server.type) }
    }

    val current = loaded
    when {
        error != null -> Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        current == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> {
            val altOn = current.second
            CtrlLabel(stringResource(R.string.speed_limits_normal))
            KbField(stringResource(R.string.torrent_limit_down), down) { down = it }
            KbField(stringResource(R.string.torrent_limit_up), up) { up = it }
            CtrlLabel(stringResource(R.string.speed_limits_alt))
            KbField(stringResource(R.string.torrent_limit_down), altDown) { altDown = it }
            KbField(stringResource(R.string.torrent_limit_up), altUp) { altUp = it }
            Text(
                stringResource(if (altOn) R.string.speed_limits_alt_on else R.string.speed_limits_alt_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CtrlPrimaryButton(
                stringResource(R.string.save),
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    fun bytes(s: String) = (s.toLongOrNull() ?: 0L).coerceIn(0L, 2_000_000L) * 1024
                    val limits = SpeedLimits(bytes(down), bytes(up), bytes(altDown), bytes(altUp))
                    saving = true
                    scope.launch {
                        runCatching { api.setSpeedLimits(limits, altOn) }
                            .onSuccess { Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show(); onSaved() }
                            .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
private fun KbField(label: String, value: String, onChange: (String) -> Unit) {
    CtrlTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(7)) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.torrent_limit_unlimited)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
