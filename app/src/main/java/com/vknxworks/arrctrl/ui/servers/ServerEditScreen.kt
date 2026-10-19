package com.vknxworks.arrctrl.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NoEncryption
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.net.ConnectionTest
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.net.Tls
import com.vknxworks.arrctrl.data.server.AuthKind
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.data.server.Urls
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.SecureWindow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.util.UUID

/** Add a server of [typeId], or edit the saved server [id]. */
@Composable
fun ServerEditScreen(id: String?, typeId: String?) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val existing = remember(id) { id?.let { c.servers.byId(it) } }
    val type = existing?.type ?: ServiceType.of(typeId)
    if (type == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.add_server))
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    ServerForm(type, existing, onDone = nav::back)
}

@Composable
private fun ServerForm(type: ServiceType, existing: Server?, onDone: () -> Unit) {
    // API keys and passwords: no screenshots or screen recording while this screen is open.
    SecureWindow()
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val label = stringResource(type.label)
    val serverId = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(existing?.url.orEmpty()) }
    // Secrets stay out of saved instance state, which Android may keep after the app is gone.
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var cert by rememberSaveable { mutableStateOf(existing?.certSha256.orEmpty()) }
    // The address a trusted certificate belongs to: it only counts for that exact scheme, host and port,
    // so changing the address to another server doesn't carry the trust over.
    var certFor by rememberSaveable { mutableStateOf(originKey(existing?.url).orEmpty()) }
    var pendingCertFor by remember { mutableStateOf("") }
    // qBittorrent: sign in with username and password unless an API key is (or was) used.
    var qbitUsesKey by rememberSaveable { mutableStateOf(existing?.let { it.apiKey.isNotBlank() } ?: false) }
    var showSecret by rememberSaveable { mutableStateOf(false) }

    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TestResult?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    var pendingCert by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // fingerprint, changed
    var certError by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val normalised = Urls.normalise(url)
    val usesKey = type.auth == AuthKind.API_KEY || qbitUsesKey
    val urlError = showErrors && normalised == null
    val keyError = showErrors && usesKey && apiKey.isBlank()
    val loginError = showErrors && !usesKey && username.isBlank()

    fun draft(): Server? {
        val u = normalised ?: return null
        return Server(
            id = serverId,
            type = type,
            name = name.trim().ifBlank { label },
            url = u,
            apiKey = if (usesKey) apiKey.trim() else "",
            username = if (usesKey) "" else username.trim(),
            password = if (usesKey) "" else password,
            certSha256 = if (u.startsWith("https://", ignoreCase = true) && originKey(u) == certFor) cert else "",
        )
    }

    fun valid(): Boolean {
        showErrors = true
        return normalised != null && (if (usesKey) apiKey.isNotBlank() else username.isNotBlank())
    }

    fun test(then: ((TestResult) -> Unit)? = null) {
        if (!valid()) return
        val d = draft() ?: return
        testing = true
        result = null
        scope.launch {
            val r = ConnectionTest.run(c.http, d, draft = true)
            result = r
            testing = false
            then?.invoke(r)
        }
    }

    fun reviewCert(changed: Boolean) {
        val u = normalised ?: return
        scope.launch {
            runCatching { Tls.fetchLeaf(u) }
                .onSuccess { pendingCertFor = originKey(u).orEmpty(); pendingCert = Tls.sha256(it) to changed }
                .onFailure { certError = true }
        }
    }

    fun save() {
        val d = draft() ?: return
        scope.launch {
            existing?.let { c.http.forget(it) }
            c.servers.save(d)
            c.status.check(d)
            onDone()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(if (existing == null) stringResource(R.string.add_service, label) else existing.name) {
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove_server)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(type.role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; result = null },
                    label = { Text(stringResource(R.string.field_address)) },
                    placeholder = { Text(stringResource(R.string.field_address_hint, type.defaultPort)) },
                    supportingText = {
                        Text(stringResource(if (urlError) R.string.field_address_invalid else R.string.field_address_help))
                    },
                    isError = urlError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (normalised != null && normalised.startsWith("http://")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NoEncryption, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.http_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (type.auth == AuthKind.QBITTORRENT) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !qbitUsesKey,
                            onClick = { qbitUsesKey = false; result = null },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.qbit_login)) }
                        SegmentedButton(
                            selected = qbitUsesKey,
                            onClick = { qbitUsesKey = true; result = null },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.qbit_api_key)) }
                    }
                }

                val secretTransform = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()
                val eye: @Composable () -> Unit = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            stringResource(if (showSecret) R.string.hide else R.string.show),
                        )
                    }
                }
                if (usesKey) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; result = null },
                        label = { Text(stringResource(R.string.field_api_key)) },
                        supportingText = {
                            Text(if (keyError) stringResource(R.string.field_required) else stringResource(type.keyHelp))
                        },
                        isError = keyError,
                        singleLine = true,
                        visualTransformation = secretTransform,
                        trailingIcon = eye,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; result = null },
                        label = { Text(stringResource(R.string.field_username)) },
                        supportingText = { if (loginError) Text(stringResource(R.string.field_required)) },
                        isError = loginError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; result = null },
                        label = { Text(stringResource(R.string.field_password)) },
                        supportingText = { Text(stringResource(R.string.key_help_qbittorrent_login)) },
                        singleLine = true,
                        visualTransformation = secretTransform,
                        trailingIcon = eye,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (cert.isNotBlank() && normalised?.startsWith("https://", ignoreCase = true) == true && originKey(normalised) == certFor) {
                Group(stringResource(R.string.cert_section)) {
                    GroupRow(
                        stringResource(R.string.cert_pinned),
                        Tls.pretty(cert).take(47) + "…",
                        Icons.Outlined.VerifiedUser,
                        trailing = { TextButton(onClick = { cert = ""; result = null }) { Text(stringResource(R.string.cert_forget)) } },
                    )
                }
            }

            // Test result
            val r = result
            if (testing || r != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (r == null || r is TestResult.Ok) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                r == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                r is TestResult.Ok -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                else -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (r == null) stringResource(R.string.status_checking) else resultText(r, type),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (r == null || r is TestResult.Ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (r is TestResult.Untrusted) {
                            TextButton(onClick = { reviewCert(r.changed) }) { Text(stringResource(R.string.cert_review)) }
                        }
                        if (certError) {
                            Text(
                                stringResource(R.string.cert_fetch_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { certError = false; test() },
                    enabled = !testing,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.test_connection)) }
                Button(
                    onClick = {
                        if (valid()) {
                            if (result is TestResult.Ok) save()
                            else test { if (it is TestResult.Ok) save() else confirmSave = true }
                        }
                    },
                    enabled = !testing,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    pendingCert?.let { (fp, changed) ->
        CertDialog(
            host = draft()?.httpUrl?.host.orEmpty(),
            fingerprint = fp,
            changed = changed,
            onTrust = { cert = fp; certFor = pendingCertFor; pendingCert = null; test() },
            onCancel = { pendingCert = null },
        )
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(stringResource(R.string.save_anyway_title)) },
            text = { Text(stringResource(R.string.save_anyway_body)) },
            confirmButton = { TextButton(onClick = { confirmSave = false; save() }) { Text(stringResource(R.string.save_anyway)) } },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_server_title, existing.name)) },
            text = { Text(stringResource(R.string.remove_server_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        c.http.forget(existing)
                        c.servers.remove(existing.id)
                        // Posters and fanart from that server shouldn't stay on the phone.
                        clearImageCaches(context)
                        onDone()
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "https://host:port" of an address, for tying a trusted certificate to exactly one server. */
private fun originKey(url: String?): String? = url?.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host.lowercase()}:${it.port}" }
