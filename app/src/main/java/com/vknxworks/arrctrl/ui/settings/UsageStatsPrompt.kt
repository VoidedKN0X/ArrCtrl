package com.vknxworks.arrctrl.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import kotlinx.coroutines.launch

/**
 * Asked once, after the first server has been added. Nothing is sent until the user says yes;
 * they can change it in Settings.
 */
@Composable
fun UsageStatsPrompt() {
    val c = LocalContext.current.container
    if (!c.telemetry.available) return
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    // The stored values, not the placeholder defaults, so the question doesn't flash for people who answered it.
    val stored by c.settings.changes.collectAsStateWithLifecycle(initialValue = null)
    val s = stored ?: return
    if (s.usageStatsAsked || servers.isEmpty()) return
    val scope = rememberCoroutineScope()
    fun answer(share: Boolean) {
        scope.launch { c.settings.update { it.copy(usageStats = share, usageStatsAsked = true) } }
    }
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.usage_stats_prompt_title)) },
        text = { Text(stringResource(R.string.usage_stats_prompt_body, stringResource(R.string.usage_stats_description))) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.usage_stats_share)) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.usage_stats_decline)) } },
    )
}
