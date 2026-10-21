package com.vknxworks.arrctrl.ui.activity

import com.vknxworks.arrctrl.ui.lock.AppLock
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Largest .torrent file accepted; real ones are a few hundred KB at most. */
private const val MAX_TORRENT_BYTES = 10L * 1024 * 1024

/**
 * Whether a .torrent file handed over by another app may be read: only content:// URIs from another app's
 * provider. file:// paths and ArrCtrl's own providers are refused, so another app can't trick ArrCtrl into
 * reading (and uploading) its own private files.
 */
fun isForeignContentUri(context: Context, uri: Uri): Boolean {
    if (uri.scheme?.lowercase() != ContentResolver.SCHEME_CONTENT) return false
    val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return false
    val owner = runCatching {
        context.packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))?.packageName
    }.getOrNull()
    return owner != context.packageName
}

/** Reads at most [limit] bytes; throws [tooBig] as the message when there's more. */
private fun readCapped(input: java.io.InputStream, limit: Long, tooBig: String): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw IllegalStateException(tooBig)
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** A file name safe to hand to qBittorrent: no folders, only plain characters, ending in .torrent. */
private fun safeTorrentName(raw: String?): String {
    val base = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("""[^A-Za-z0-9 ._()\[\]-]"""), "_")
        .trim().trimStart('.').take(120)
    if (base.isBlank()) return "file.torrent"
    return if (base.endsWith(".torrent", ignoreCase = true)) base else "$base.torrent"
}

/** Links that qBittorrent can add: magnets, and http(s) links to .torrent files. */
fun extractTorrentLinks(text: String): List<String> =
    Regex("""(magnet:\?[^\s"'<>]+|https?://[^\s"'<>]+)""", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.distinct().toList()

@Composable
fun AddTorrentScreen(initialLink: String?, initialFile: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val qbits = servers.filter { it.type == ServiceType.QBITTORRENT }

    if (qbits.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.torrent_add_title))
            MessageView(
                Icons.Outlined.Download,
                stringResource(R.string.torrent_add_no_qbit),
                stringResource(R.string.torrent_add_no_qbit_body),
                actionLabel = stringResource(R.string.add_service, stringResource(R.string.service_qbittorrent)),
                onAction = { nav.navigate(ServerEditRoute(type = ServiceType.QBITTORRENT.id)) },
            )
        }
        return
    }

    var link by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    var fileUri by rememberSaveable { mutableStateOf(initialFile) }
    var serverId by rememberSaveable { mutableStateOf(qbits.first().id) }
    val server = qbits.firstOrNull { it.id == serverId } ?: qbits.first()
    var category by rememberSaveable { mutableStateOf("") }
    var stopped by rememberSaveable { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(server.id) {
        categories = runCatching { c.qbit(server).categories() }.getOrDefault(emptyList())
        if (category.isNotEmpty() && category !in categories) category = ""
    }

    val fileName = remember(fileUri) { fileUri?.let { displayName(context, Uri.parse(it)) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { fileUri = uri.toString(); link = "" }
    }

    val links = extractTorrentLinks(link)
    val canAdd = !adding && (fileUri != null || links.isNotEmpty())
    val addedText = stringResource(R.string.torrent_added)
    val tooBig = stringResource(R.string.torrent_file_too_big)
    val unreadable = stringResource(R.string.torrent_file_unreadable)
    val notTorrent = stringResource(R.string.torrent_file_invalid)

    fun add() {
        adding = true
        scope.launch {
            val result = runCatching {
                val api = c.qbit(server)
                val f = fileUri
                if (f != null) {
                    val uri = Uri.parse(f)
                    if (!isForeignContentUri(context, uri)) throw IllegalStateException(unreadable)
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input -> readCapped(input, MAX_TORRENT_BYTES, tooBig) }
                    } ?: throw IllegalStateException(unreadable)
                    // Every .torrent file is a bencoded dictionary, so it starts with "d".
                    if (bytes.isEmpty() || bytes[0] != 'd'.code.toByte()) throw IllegalStateException(notTorrent)
                    api.addFile(safeTorrentName(fileName), bytes, category.ifBlank { null }, stopped)
                } else {
                    api.addLinks(links, category.ifBlank { null }, stopped)
                }
            }
            adding = false
            result
                .onSuccess {
                    Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
                    nav.back()
                }
                .onFailure { Toast.makeText(context, it.message ?: it::class.simpleName.orEmpty(), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.torrent_add_title)) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fileUri == null) {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(stringResource(R.string.torrent_add_link)) },
                        placeholder = { Text("magnet:?xt=urn:btih:…") },
                        supportingText = { Text(stringResource(R.string.torrent_add_link_help)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    )
                    OutlinedButton(
                        onClick = { AppLock.expectReturn(); picker.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.FileOpen, null)
                        Text(stringResource(R.string.torrent_add_pick_file), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (fileUri != null) {
                Group(stringResource(R.string.torrent_add_file)) {
                    GroupRow(
                        fileName ?: stringResource(R.string.torrent_file),
                        icon = Icons.Outlined.Description,
                        trailing = {
                            IconButton(onClick = { fileUri = null }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove)) }
                        },
                    )
                }
            }
            Group(stringResource(R.string.torrent_add_options)) {
                if (qbits.size > 1) {
                    GroupChoice(stringResource(R.string.torrent_add_server), qbits.map { it.id to it.name }, serverId, Icons.Outlined.Dns) { serverId = it }
                }
                GroupChoice(
                    stringResource(R.string.torrent_category),
                    listOf("" to stringResource(R.string.torrent_category_none)) + categories.map { it to it },
                    category,
                    Icons.Outlined.Category,
                ) { category = it }
                GroupSwitch(stringResource(R.string.torrent_add_stopped), stringResource(R.string.torrent_add_stopped_help), stopped) { stopped = it }
            }
            Button(
                onClick = { add() },
                enabled = canAdd,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            ) {
                Text(
                    if (fileUri == null && links.size > 1) androidx.compose.ui.res.pluralStringResource(R.plurals.torrent_add_n, links.size, links.size)
                    else stringResource(R.string.torrent_add),
                )
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String? = if (!isForeignContentUri(context, uri)) null else runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment
