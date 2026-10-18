package com.vknxworks.arrctrl.ui.activity

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.ArrApi
import com.vknxworks.arrctrl.data.api.EpisodeEntry
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.ImportFile
import com.vknxworks.arrctrl.data.api.LibraryEntry
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Chip
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.servers.resultText
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun ManualImportScreen(serverId: String, downloadId: String, title: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.manual_import))
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.arr(server) }
    val sonarr = server.type == ServiceType.SONARR

    var files by remember { mutableStateOf<List<ImportFile>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mapping by remember { mutableStateOf<ImportFile?>(null) }
    var importing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(server, downloadId) {
        runCatching { api.importFiles(downloadId) }
            .onSuccess { list ->
                files = list
                // Pre-select files that are matched and have nothing against them.
                selected = list.filter { it.rejections.isEmpty() && it.isMapped(sonarr) }.map { it.path }.toSet()
            }
            .onFailure { error = it.toTestResult() }
    }

    val doneText = stringResource(R.string.manual_import_started)
    fun runImport() {
        val chosen = files.orEmpty().filter { it.path in selected }
        importing = true
        scope.launch {
            runCatching { api.manualImport(chosen, downloadId) }
                .onSuccess {
                    Toast.makeText(context, doneText, Toast.LENGTH_SHORT).show()
                    nav.back()
                }
                .onFailure {
                    importing = false
                    Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show()
                }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.manual_import)) },
        bottomBar = {
            val list = files
            if (list != null && list.isNotEmpty()) {
                val chosen = list.filter { it.path in selected }
                Button(
                    onClick = { if (chosen.any { it.rejections.isNotEmpty() }) confirm = true else runImport() },
                    enabled = chosen.isNotEmpty() && chosen.all { it.isMapped(sonarr) } && !importing,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
                ) {
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.manual_import_files, chosen.size, chosen.size))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = files
            when {
                error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type))
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> MessageView(Icons.Outlined.FolderOff, stringResource(R.string.manual_import_none), stringResource(R.string.manual_import_none_body))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                        )
                    }
                    items(list, key = { it.path }) { f ->
                        ImportRow(
                            f,
                            sonarr = sonarr,
                            checked = f.path in selected,
                            onCheck = { on -> selected = if (on) selected + f.path else selected - f.path },
                            onMap = { mapping = f },
                        )
                    }
                }
            }
        }
    }

    mapping?.let { f ->
        if (sonarr) {
            SeriesMapper(api, f, onDismiss = { mapping = null }) { updated ->
                files = files?.map { if (it.path == f.path) updated else it }
                selected = selected + f.path
                mapping = null
            }
        } else {
            MovieMapper(api, f, onDismiss = { mapping = null }) { updated ->
                files = files?.map { if (it.path == f.path) updated else it }
                selected = selected + f.path
                mapping = null
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.manual_import_rejected_title)) },
            text = { Text(stringResource(R.string.manual_import_rejected_body)) },
            confirmButton = { TextButton(onClick = { confirm = false; runImport() }) { Text(stringResource(R.string.manual_import_anyway)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun ImportFile.isMapped(sonarr: Boolean): Boolean = if (sonarr) seriesId != null && episodeIds.isNotEmpty() else movieId != null

@Composable
private fun ImportRow(f: ImportFile, sonarr: Boolean, checked: Boolean, onCheck: (Boolean) -> Unit, onMap: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = Gutter, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked, onCheckedChange = onCheck)
        Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                f.quality?.let { Chip(it) }
                Text(Format.bytes(f.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val target = if (sonarr) listOfNotNull(f.seriesTitle, f.episodeLabel).joinToString(" · ") else f.movieTitle.orEmpty()
            Text(
                if (target.isBlank() || !f.isMapped(sonarr)) stringResource(R.string.manual_import_unmatched) else target,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onMap).padding(vertical = 4.dp),
            )
            f.rejections.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Radarr: pick which movie a file belongs to. */
@Composable
private fun MovieMapper(api: ArrApi, f: ImportFile, onDismiss: () -> Unit, onPicked: (ImportFile) -> Unit) {
    LibraryPicker(api, onDismiss) { m ->
        onPicked(f.copy(movieId = m.id, movieTitle = m.year?.let { "${m.title} ($it)" } ?: m.title))
    }
}

/** Sonarr: pick the series, then one or more episodes. */
@Composable
private fun SeriesMapper(api: ArrApi, f: ImportFile, onDismiss: () -> Unit, onPicked: (ImportFile) -> Unit) {
    var series by remember { mutableStateOf<LibraryEntry?>(f.seriesId?.let { LibraryEntry(it, f.seriesTitle.orEmpty(), null) }) }
    var choosingSeries by remember { mutableStateOf(f.seriesId == null) }
    val s = series
    if (choosingSeries || s == null) {
        LibraryPicker(api, onDismiss) { picked -> series = picked; choosingSeries = false }
        return
    }
    EpisodePicker(api, s, initial = f.episodeIds.toSet(), onDismiss = onDismiss, onChangeSeries = { choosingSeries = true }) { eps ->
        val label = if (eps.size == 1) listOfNotNull(Format.episode(eps[0].season, eps[0].number), eps[0].title).joinToString(" · ")
        else eps.mapNotNull { Format.episode(it.season, it.number) }.joinToString(", ")
        onPicked(
            f.copy(
                seriesId = s.id,
                seriesTitle = s.title,
                seasonNumber = eps.firstOrNull()?.season,
                episodeIds = eps.map { it.id },
                episodeLabel = label,
            ),
        )
    }
}

@Composable
private fun LibraryPicker(api: ArrApi, onDismiss: () -> Unit, onPick: (LibraryEntry) -> Unit) {
    var all by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(api) { runCatching { api.library() }.onSuccess { all = it }.onFailure { failed = true } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val list = all
            when {
                failed -> Text(stringResource(R.string.load_failed), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val q = query.trim()
                    val shown = if (q.isEmpty()) list else list.filter { it.title.contains(q, ignoreCase = true) }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                        items(shown, key = { it.id }) { e ->
                            Text(
                                e.year?.let { "${e.title} ($it)" } ?: e.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(e) }.padding(horizontal = 24.dp, vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodePicker(
    api: ArrApi,
    series: LibraryEntry,
    initial: Set<Int>,
    onDismiss: () -> Unit,
    onChangeSeries: () -> Unit,
    onPick: (List<EpisodeEntry>) -> Unit,
) {
    var eps by remember(series.id) { mutableStateOf<List<EpisodeEntry>?>(null) }
    var failed by remember(series.id) { mutableStateOf(false) }
    var chosen by remember(series.id) { mutableStateOf(initial) }
    LaunchedEffect(series.id) { runCatching { api.episodes(series.id) }.onSuccess { eps = it }.onFailure { failed = true } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(series.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onChangeSeries) { Text(stringResource(R.string.manual_import_change_series)) }
            }
            val list = eps
            when {
                failed -> Text(stringResource(R.string.load_failed), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                        items(list, key = { it.id }) { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { chosen = if (e.id in chosen) chosen - e.id else chosen + e.id }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(e.id in chosen, onCheckedChange = { on -> chosen = if (on) chosen + e.id else chosen - e.id })
                                Text(
                                    listOfNotNull(Format.episode(e.season, e.number), e.title).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (e.hasFile) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { onPick(list.filter { it.id in chosen }) },
                        enabled = chosen.isNotEmpty(),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    ) { Text(stringResource(R.string.manual_import_use_episodes)) }
                }
            }
        }
    }
}
