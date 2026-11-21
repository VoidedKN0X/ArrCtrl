package com.vknxworks.arrctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.BuildConfig
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.data.settings.BackupCrypto
import com.vknxworks.arrctrl.data.settings.BackupFormatException
import com.vknxworks.arrctrl.data.settings.ConfigBackup
import com.vknxworks.arrctrl.data.settings.ConfigBackups
import com.vknxworks.arrctrl.ui.components.CtrlAlertDialog
import com.vknxworks.arrctrl.ui.components.CtrlTextField
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.SecureWindow
import com.vknxworks.arrctrl.ui.lock.AppLock
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Encrypted backup of ArrCtrl's own configuration: the settings for its look and lists and the list of
 * servers (type, name, address, user name), in a file sealed with a password you choose. API keys,
 * passwords, trusted certificates, the app lock and the notification settings are never in it.
 * Restoring replaces those settings after a confirmation and offers the servers that aren't on this phone
 * one by one through the normal Add server screen.
 */
@Composable
fun BackupGroup() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val offers by c.restoreOffers.collectAsStateWithLifecycle()

    var askNewPassword by remember { mutableStateOf(false) }
    // Kept only between choosing the password and picking where the file goes; never saved anywhere.
    var newPassword by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    var wrongPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ConfigBackup?>(null) }

    fun toast(id: Int) = Toast.makeText(context, id, Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val password = newPassword
        newPassword = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                val plain = ConfigBackups.export(c.settings.current(), c.servers.servers.value, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                // Stretching the password takes a moment: off the main thread.
                val file = withContext(Dispatchers.Default) { BackupCrypto.seal(plain.toByteArray(Charsets.UTF_8), password.toCharArray()) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(file.toByteArray(Charsets.US_ASCII)) } ?: throw IOException("no stream")
                }
            }.isSuccess
            busy = false
            toast(if (ok) R.string.backup_saved else R.string.backup_save_failed)
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // A backup is a few kilobytes; anything far bigger isn't one.
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_BYTES) return@use null
                        }
                        out.toString(Charsets.UTF_8.name())
                    }
                }
            }.getOrNull()
            when {
                text == null -> toast(R.string.backup_read_failed)
                !BackupCrypto.looksLikeBackup(text) -> toast(R.string.backup_not_valid)
                else -> {
                    wrongPassword = false
                    opened = text
                }
            }
        }
    }

    Group(stringResource(R.string.backup_title), footer = stringResource(R.string.backup_footer)) {
        GroupRow(
            stringResource(R.string.backup_save),
            stringResource(R.string.backup_save_sub),
            Icons.Outlined.FileDownload,
            enabled = !busy,
            onClick = { askNewPassword = true },
        )
        GroupRow(
            stringResource(R.string.backup_restore),
            stringResource(R.string.backup_restore_sub),
            Icons.Outlined.FileUpload,
            enabled = !busy,
            onClick = { AppLock.expectReturn(); open.launch(arrayOf("*/*")) },
        )
    }

    // Servers from the restored backup that aren't on this phone yet: each opens the normal Add server screen.
    val waiting = ConfigBackups.missing(offers, servers)
    if (waiting.isNotEmpty()) {
        Group(stringResource(R.string.backup_offers_title), footer = stringResource(R.string.backup_offers_footer)) {
            waiting.forEach { s ->
                val type = ServiceType.of(s.type)
                GroupRow(
                    s.name.ifBlank { type?.let { stringResource(it.label) } ?: s.type },
                    listOfNotNull(type?.let { stringResource(it.label) }, s.url).joinToString(" · "),
                    Icons.Outlined.Add,
                    showChevron = true,
                    onClick = { nav.navigate(ServerEditRoute(type = s.type, name = s.name, url = s.url, username = s.username)) },
                )
            }
            GroupRow(stringResource(R.string.backup_offers_dismiss), onClick = { c.restoreOffers.value = emptyList() })
        }
    }

    if (askNewPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            body = stringResource(R.string.backup_password_body, BackupCrypto.MIN_PASSWORD),
            confirm = stringResource(R.string.backup_password_next),
            repeat = true,
            error = null,
            busy = false,
            onDismiss = { askNewPassword = false },
        ) { password ->
            askNewPassword = false
            newPassword = password
            AppLock.expectReturn()
            save.launch("arrctrl-backup-${LocalDate.now()}.arrctrl")
        }
    }

    opened?.let { text ->
        PasswordDialog(
            title = stringResource(R.string.backup_open_title),
            body = stringResource(R.string.backup_open_body),
            confirm = stringResource(R.string.backup_open_confirm),
            repeat = false,
            error = if (wrongPassword) stringResource(R.string.backup_wrong_password) else null,
            busy = busy,
            onDismiss = { if (!busy) opened = null },
        ) { password ->
            busy = true
            wrongPassword = false
            scope.launch {
                val result = runCatching {
                    val plain = withContext(Dispatchers.Default) { BackupCrypto.open(text, password.toCharArray()) }
                    ConfigBackups.parse(plain.toString(Charsets.UTF_8))
                }
                busy = false
                result.onSuccess {
                    opened = null
                    preview = it
                }.onFailure { e ->
                    when {
                        e is BackupCrypto.WrongPassword -> wrongPassword = true
                        e is BackupFormatException && e.newer -> { opened = null; toast(R.string.backup_too_new) }
                        else -> { opened = null; toast(R.string.backup_not_valid) }
                    }
                }
            }
        }
    }

    preview?.let { b ->
        val missing = ConfigBackups.missing(b.servers, servers)
        val here = b.servers.size - missing.size
        CtrlAlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_restore_body, b.createdAt?.take(10) ?: "?", b.appVersion ?: "?"))
                    if (here > 0) Text(pluralStringResource(R.plurals.backup_servers_here, here, here))
                    if (missing.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.backup_servers_missing, missing.size, missing.size))
                        missing.forEach { s ->
                            val label = ServiceType.of(s.type)?.let { stringResource(it.label) } ?: s.type
                            val plain = s.url.startsWith("http://", ignoreCase = true)
                            Text(
                                "$label · ${s.url}" + if (plain) " · " + stringResource(R.string.server_not_encrypted) else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(stringResource(R.string.backup_restore_keeps))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    scope.launch {
                        val ok = runCatching { c.settings.update { ConfigBackups.apply(it, b.settings) } }.isSuccess
                        if (ok) c.restoreOffers.value = b.servers
                        toast(if (ok) R.string.backup_restored else R.string.backup_restore_failed)
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.cancel)) } },
            shielded = true,
        )
    }
}

/** Asks for a backup password ([repeat]: twice, for a new backup). Screenshots are blocked while it shows. */
@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirm: String,
    repeat: Boolean,
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    SecureWindow()
    // Not saved with the screen's state: a password shouldn't outlive the dialog.
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = repeat && first.isNotEmpty() && first.length < BackupCrypto.MIN_PASSWORD
    val mismatch = repeat && second.isNotEmpty() && second != first
    val firstHelp: (@Composable () -> Unit)? = when {
        tooShort -> { { Text(stringResource(R.string.backup_password_short, BackupCrypto.MIN_PASSWORD)) } }
        error != null -> { { Text(error) } }
        else -> null
    }
    val secondHelp: (@Composable () -> Unit)? = if (mismatch) { { Text(stringResource(R.string.backup_password_mismatch)) } } else null
    val ready = !busy && first.isNotEmpty() && (!repeat || (first.length >= BackupCrypto.MIN_PASSWORD && second == first))
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                CtrlTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    supportingText = firstHelp,
                    isError = tooShort || error != null,
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (repeat) {
                    CtrlTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text(stringResource(R.string.backup_password_again)) },
                        supportingText = secondHelp,
                        isError = mismatch,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(first) }, enabled = ready) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        shielded = true,
    )
}

private const val MAX_BYTES = 1024 * 1024
