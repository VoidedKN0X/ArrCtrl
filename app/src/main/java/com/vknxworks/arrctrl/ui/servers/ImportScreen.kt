package com.vknxworks.arrctrl.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.ImportFormat
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.SecureWindow
import com.vknxworks.arrctrl.data.net.Tls
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** Adds several servers at once from the ArrCtrl JSON format, pasted or scanned from a QR code. */
@Composable
fun ImportScreen() {
    // Pasted or scanned settings hold API keys and passwords: no screenshots or screen recording here.
    SecureWindow()
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val saved by c.servers.servers.collectAsStateWithLifecycle()

    // Not saveable: the text holds API keys and passwords, which mustn't end up in saved instance state.
    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<ImportFormat.Entry>?>(null) }
    var formatError by remember { mutableStateOf(false) }

    fun parse(input: String) {
        text = input
        try {
            parsed = ImportFormat.parse(input)
            formatError = false
        } catch (_: ImportFormat.FormatException) {
            parsed = null
            formatError = true
        }
    }

    var scanning by remember { mutableStateOf(false) }
    if (scanning) {
        QrScanDialog(
            onResult = { code ->
                scanning = false
                parse(code)
            },
            onDismiss = { scanning = false },
        )
    }

    // Names left out of the import get the service name, numbered when there's more than one.
    val labels = com.vknxworks.arrctrl.data.server.ServiceType.entries.associateWith { stringResource(it.label) }
    val ready: List<Server> = remember(parsed, saved, labels) {
        val oks = parsed.orEmpty().filterIsInstance<ImportFormat.Entry.Ok>().map { it.server }
        val counts = mutableMapOf<com.vknxworks.arrctrl.data.server.ServiceType, Int>()
        oks.map { s ->
            if (s.name.isNotBlank()) s
            else {
                val n = (counts[s.type] ?: saved.count { it.type == s.type }) + 1
                counts[s.type] = n
                val base = labels.getValue(s.type)
                s.copy(name = if (n == 1) base else "$base $n")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.import_title)) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.import_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { scanning = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null)
                        Text(stringResource(R.string.import_scan), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        onClick = { clipboard.getText()?.text?.let(::parse) },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, null)
                        Text(stringResource(R.string.import_paste), Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = ::parse,
                    label = { Text(stringResource(R.string.import_field)) },
                    isError = formatError,
                    supportingText = { if (formatError) Text(stringResource(R.string.import_bad_format)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
            }

            val entries = parsed
            if (!entries.isNullOrEmpty()) {
                Group(stringResource(R.string.import_preview)) {
                    var okIndex = 0
                    entries.forEach { e ->
                        when (e) {
                            is ImportFormat.Entry.Ok -> {
                                val s = ready[okIndex++]
                                val updates = saved.any { it.sameEndpoint(s) }
                                GroupRow(
                                    s.name,
                                    listOfNotNull(
                                        labels.getValue(s.type),
                                        s.url,
                                        stringResource(if (updates) R.string.import_updates else R.string.import_new),
                                        if (!s.isEncrypted) stringResource(R.string.server_not_encrypted) else null,
                                        // A certificate in the import is trusted for this server, so show which one.
                                        s.certSha256.takeIf { it.isNotBlank() }?.let { stringResource(R.string.import_cert, Tls.pretty(it)) },
                                    ).joinToString(" · "),
                                    s.type.icon,
                                )
                            }
                            is ImportFormat.Entry.Bad -> GroupRow(
                                stringResource(R.string.import_entry, e.index),
                                problemText(e),
                                Icons.Outlined.ErrorOutline,
                                iconTint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        ready.forEach { c.http.forget(it) }
                        c.servers.import(ready)
                        // The Servers list re-checks every server when the saved list changes.
                        nav.back()
                    }
                },
                enabled = ready.isNotEmpty(),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Text(
                    if (ready.isEmpty()) stringResource(R.string.import_add_none)
                    else androidx.compose.ui.res.pluralStringResource(R.plurals.import_add, ready.size, ready.size),
                )
            }
            Text(
                stringResource(R.string.import_format_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter + 4.dp),
            )
        }
    }
}

@Composable
private fun problemText(e: ImportFormat.Entry.Bad): String = when (e.reason) {
    ImportFormat.Problem.UNKNOWN_TYPE -> stringResource(R.string.import_problem_type, e.detail)
    ImportFormat.Problem.BAD_URL -> stringResource(R.string.import_problem_url, e.detail)
    ImportFormat.Problem.MISSING_KEY -> stringResource(R.string.import_problem_key)
    ImportFormat.Problem.MISSING_LOGIN -> stringResource(R.string.import_problem_login)
}
