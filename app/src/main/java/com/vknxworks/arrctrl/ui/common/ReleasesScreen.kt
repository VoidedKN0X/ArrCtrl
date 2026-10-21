package com.vknxworks.arrctrl.ui.common

import android.widget.Toast
import com.vknxworks.arrctrl.ui.servers.failureText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.Release
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.movies.signed
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

private enum class ReleaseSort(val label: Int) {
    BEST(R.string.release_sort_best),
    SEEDERS(R.string.release_sort_seeders),
    SIZE(R.string.release_sort_size),
    AGE(R.string.release_sort_age),
    SCORE(R.string.release_sort_score),
}

/** "3h", "5d", "2y": a release's age at a glance. */
@Composable
fun releaseAge(hours: Double?): String? {
    val h = hours ?: return null
    return when {
        h < 1 -> stringResource(R.string.age_under_hour)
        h < 48 -> stringResource(R.string.age_hours, h.toInt())
        h < 24 * 365 -> stringResource(R.string.age_days, (h / 24).toInt())
        else -> stringResource(R.string.age_years, (h / (24 * 365)).toInt())
    }
}

/**
 * Interactive search: asks the server's indexers for releases and grabs the one you tap.
 * Works for Radarr (a movie) and Sonarr (an episode or a season), given how to [load] and [grab].
 */
@Composable
fun ReleasesScreen(title: String, type: ServiceType, load: suspend () -> List<Release>, grab: suspend (Release) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<Release>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var sortName by rememberSaveable { mutableStateOf(ReleaseSort.BEST.name) }
    var hideRejected by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Release?>(null) }
    var grabbed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(attempt) {
        releases = null
        error = null
        runCatching { load() }.onSuccess { releases = it }.onFailure { error = it.toTestResult() }
    }

    val sentText = stringResource(R.string.release_sent)
    fun doGrab(r: Release) {
        scope.launch {
            runCatching { grab(r) }
                .onSuccess {
                    grabbed = grabbed + r.guid
                    Toast.makeText(context, sentText, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, failureText(context, it), Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val list = releases
            when {
                error != null -> MessageView(
                    Icons.Outlined.ErrorOutline,
                    resultText(error!!, type),
                    actionLabel = stringResource(R.string.retry),
                    onAction = { attempt++ },
                )
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.release_searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                list.isEmpty() -> MessageView(Icons.Outlined.SearchOff, stringResource(R.string.release_none), stringResource(R.string.release_none_body))
                else -> {
                    val sort = ReleaseSort.valueOf(sortName)
                    val shown = list.filter { !hideRejected || !it.rejected }.let { l ->
                        when (sort) {
                            // Radarr and Sonarr rank releases themselves (lower weight is better); approved first.
                            ReleaseSort.BEST -> l.sortedWith(compareBy<Release> { it.rejected }.thenBy { it.releaseWeight })
                            ReleaseSort.SEEDERS -> l.sortedByDescending { it.seeders ?: -1 }
                            ReleaseSort.SIZE -> l.sortedByDescending { it.size }
                            ReleaseSort.AGE -> l.sortedBy { it.ageHours ?: Double.MAX_VALUE }
                            ReleaseSort.SCORE -> l.sortedWith(compareByDescending<Release> { it.customFormatScore }.thenByDescending { it.qualityWeight })
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReleaseSort.entries.forEach { s ->
                            FilterChip(selected = s == sort, onClick = { sortName = s.name }, label = { Text(stringResource(s.label)) })
                        }
                        FilterChip(selected = hideRejected, onClick = { hideRejected = !hideRejected }, label = { Text(stringResource(R.string.release_hide_rejected)) })
                    }
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.release_count, list.size, list.size, list.count { !it.rejected }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(shown, key = { it.guid + "|" + it.indexerId }) { r ->
                            ReleaseRow(r, sent = r.guid in grabbed) { if (r.rejected) confirm = r else doGrab(r) }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { r ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.release_rejected_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.title, style = MaterialTheme.typography.bodySmall)
                    r.rejections.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { confirm = null; doGrab(r) }) { Text(stringResource(R.string.release_grab_anyway)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReleaseRow(r: Release, sent: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            r.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (r.rejected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            r.quality?.let { Chip(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
            if (r.customFormatScore != 0) {
                Chip(
                    signed(r.customFormatScore),
                    if (r.customFormatScore > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    if (r.customFormatScore > 0) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError,
                )
            }
            r.customFormats.take(4).forEach { Chip(it) }
            r.languages.filter { it.isNotBlank() }.take(2).forEach { Chip(it) }
            if (sent) Chip(stringResource(R.string.release_sent_chip), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            listOfNotNull(
                r.indexer,
                Format.bytes(r.size),
                releaseAge(r.ageHours),
                if (r.protocol == "torrent" || r.seeders != null) stringResource(R.string.release_peers, r.seeders ?: 0, r.leechers ?: 0) else r.protocol,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        r.rejections.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
    }
}
