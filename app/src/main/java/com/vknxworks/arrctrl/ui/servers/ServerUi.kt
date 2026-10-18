package com.vknxworks.arrctrl.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.net.Tls
import com.vknxworks.arrctrl.data.server.ServerStatus
import com.vknxworks.arrctrl.data.server.ServiceType

val ServiceType.icon: ImageVector
    get() = when (this) {
        ServiceType.RADARR -> Icons.Outlined.Movie
        ServiceType.SONARR -> Icons.Outlined.Tv
        ServiceType.QBITTORRENT -> Icons.Outlined.Download
        ServiceType.PROWLARR -> Icons.Outlined.Search
        ServiceType.BAZARR -> Icons.Outlined.Subtitles
        ServiceType.AUTOBRR -> Icons.Outlined.RssFeed
        ServiceType.CLEANUPARR -> Icons.Outlined.CleaningServices
    }

/** One line describing a connection test result for [type]. */
@Composable
fun resultText(result: TestResult, type: ServiceType): String {
    val service = stringResource(type.label)
    return when (result) {
        is TestResult.Ok ->
            if (result.version.isNullOrBlank()) stringResource(R.string.test_ok)
            else stringResource(R.string.test_ok_version, service, result.version)
        is TestResult.WrongService ->
            if (result.found.isNullOrBlank()) stringResource(R.string.test_wrong_service, service)
            else stringResource(R.string.test_wrong_service_found, result.found, service)
        TestResult.BadKey -> stringResource(R.string.test_bad_key)
        TestResult.BadLogin -> stringResource(R.string.test_bad_login)
        TestResult.Banned -> stringResource(R.string.test_banned)
        is TestResult.Untrusted ->
            if (result.changed) stringResource(R.string.test_cert_changed) else stringResource(R.string.test_cert_untrusted)
        is TestResult.TlsFailed -> stringResource(R.string.test_tls_failed)
        TestResult.HostNotFound -> stringResource(R.string.test_host_not_found)
        TestResult.Refused -> stringResource(R.string.test_refused)
        TestResult.Timeout -> stringResource(R.string.test_timeout)
        is TestResult.Unreachable -> stringResource(R.string.test_unreachable)
        is TestResult.HttpError -> stringResource(R.string.test_http_error, result.code)
    }
}

/** Short status for a row in the Servers list. */
@Composable
fun checkText(check: ServerStatus.Check?, type: ServiceType): String = when (check) {
    null, ServerStatus.Check.Checking -> stringResource(R.string.status_checking)
    is ServerStatus.Check.Done -> resultText(check.result, type)
}

@Composable
fun CheckIcon(check: ServerStatus.Check?) {
    when (check) {
        null, ServerStatus.Check.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        is ServerStatus.Check.Done ->
            if (check.result is TestResult.Ok) {
                Icon(Icons.Outlined.CheckCircle, stringResource(R.string.status_connected), tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.status_problem), tint = MaterialTheme.colorScheme.error)
            }
    }
}

/** Shows a self-signed certificate's fingerprint and asks whether to trust exactly that certificate. */
@Composable
fun CertDialog(host: String, fingerprint: String, changed: Boolean, onTrust: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.GppMaybe, null) },
        title = { Text(stringResource(if (changed) R.string.cert_changed_title else R.string.cert_trust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (changed) R.string.cert_changed_body else R.string.cert_trust_body, host),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(Tls.pretty(fingerprint), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onTrust) { Text(stringResource(R.string.cert_trust)) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
