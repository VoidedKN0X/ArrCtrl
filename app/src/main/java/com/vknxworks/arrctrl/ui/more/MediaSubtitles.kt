package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.BazarrApi
import com.vknxworks.arrctrl.data.api.BazarrLanguage
import com.vknxworks.arrctrl.data.api.BazarrMedia
import com.vknxworks.arrctrl.data.api.BazarrSub
import com.vknxworks.arrctrl.data.api.Format
import com.vknxworks.arrctrl.data.api.SubLanguage
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MediaSubtitlesRoute
import com.vknxworks.arrctrl.ui.nav.SubtitleSearchRoute
import com.vknxworks.arrctrl.ui.servers.resultText
import kotlinx.coroutines.launch

/**
 * Finds which Bazarr server has a Radarr movie or Sonarr series. Bazarr keys items by the *arr id, so the
 * id is checked against IMDb (movies), TVDB (series) or the title, in case another Bazarr uses another
 * Radarr or Sonarr where the same id is a different item.
 */
object BazarrLink {
    private fun same(a: String?, b: String?) =
        a != null && b != null && a.lowercase().filter { it.isLetterOrDigit() } == b.lowercase().filter { it.isLetterOrDigit() }

    private fun bazarrs(c: AppContainer): List<Server> = c.servers.servers.value.filter { it.type == ServiceType.BAZARR }

    suspend fun movie(c: AppContainer, radarrId: Int, imdbId: String?, title: String): Pair<Server, BazarrMedia>? {
        for (s in bazarrs(c)) {
            val m = runCatching { c.bazarr(s).movie(radarrId) }.getOrNull() ?: continue
            val ok = if (imdbId != null && m.imdbId != null) imdbId.equals(m.imdbId, ignoreCase = true) else same(m.title, title)
            if (ok) return s to m
        }
        return null
    }

    suspend fun series(c: AppContainer, seriesId: Int, tvdbId: Int?, title: String): Pair<Server, BazarrMedia>? {
        for (s in bazarrs(c)) {
            val m = runCatching { c.bazarr(s).series(seriesId) }.getOrNull() ?: continue
            val ok = if (tvdbId != null && m.tvdbId != null) tvdbId == m.tvdbId else same(m.title, title)
            if (ok) return s to m
        }
        return null
    }
}

private fun subLabel(name: String, hi: Boolean, forced: Boolean, hiText: String, forcedText: String) =
    name + (if (hi) " $hiText" else "") + (if (forced) " $forcedText" else "")

/** "Subtitles" block on a movie page; hidden when no Bazarr has the movie. */
@Composable
fun MovieSubtitlesGroup(radarrId: Int, imdbId: String?, title: String, hasFile: Boolean) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    var link by remember { mutableStateOf<Pair<Server, BazarrMedia>?>(null) }
    LaunchedEffect(radarrId) { link = BazarrLink.movie(c, radarrId, imdbId, title) }
    val (server, m) = link ?: return
    val hiText = stringResource(R.string.bazarr_hi_short)
    val forcedText = stringResource(R.string.bazarr_forced_short)
    Group(stringResource(R.string.detail_subtitles)) {
        GroupRow(
            stringResource(R.string.subs_manage),
            when {
                !hasFile -> stringResource(R.string.subs_no_file)
                m.subtitles.isEmpty() -> stringResource(R.string.subs_none_yet)
                else -> m.subtitles.distinctBy { it.code + it.hi + it.forced }.joinToString(", ") { subLabel(it.name, it.hi, it.forced, hiText, forcedText) }
            },
            Icons.Outlined.Subtitles,
            showChevron = true,
            onClick = { nav.navigate(MediaSubtitlesRoute(server.id, radarrId = radarrId, title = title)) },
        )
        if (m.missing.isNotEmpty()) {
            GroupRow(
                stringResource(R.string.subs_missing),
                m.missing.joinToString(", ") { subLabel(it.name.ifBlank { it.label }, it.hi, it.forced, hiText, forcedText) },
                Icons.Outlined.SubtitlesOff,
                iconTint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Languages profile and search for a whole series; hidden when no Bazarr has it. */
@Composable
fun SeriesSubtitlesGroup(seriesId: Int, tvdbId: Int?, title: String) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf<Pair<Server, BazarrMedia>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(seriesId, reload) {
        link = BazarrLink.series(c, seriesId, tvdbId, title)
        link?.let { (s, _) -> runCatching { c.bazarr(s).profiles() }.onSuccess { p -> names = p.associate { it.id to it.name } } }
    }
    val (server, m) = link ?: return
    val api = remember(server) { c.bazarr(server) }
    val started = stringResource(R.string.movie_search_started)
    Group(stringResource(R.string.detail_subtitles), footer = stringResource(R.string.subs_series_footer)) {
        GroupRow(
            stringResource(R.string.bazarr_languages_title),
            m.profileId?.let { names[it] } ?: stringResource(R.string.bazarr_no_profile),
            Icons.Outlined.Translate,
            showChevron = true,
            onClick = { editing = true },
        )
        GroupRow(stringResource(R.string.subs_search_missing_series), icon = Icons.Outlined.Search, onClick = {
            scope.launch {
                runCatching { api.searchSeries(seriesId) }
                    .onSuccess { Toast.makeText(context, started, Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
            }
        })
    }
    if (editing) LanguagesProfileDialog(api, null, seriesId, onDismiss = { editing = false }) { reload++ }
}

/** Button in an episode's sheet that opens its subtitles; hidden when no Bazarr has the series. */
@Composable
fun EpisodeSubtitlesButton(seriesId: Int, tvdbId: Int?, seriesTitle: String, episodeId: Int, title: String, onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    var server by remember { mutableStateOf<Server?>(null) }
    LaunchedEffect(seriesId) { server = BazarrLink.series(c, seriesId, tvdbId, seriesTitle)?.first }
    val s = server ?: return
    OutlinedButton(
        onClick = { onOpen(); nav.navigate(MediaSubtitlesRoute(s.id, seriesId = seriesId, episodeId = episodeId, title = title)) },
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Subtitles, null)
        Text(stringResource(R.string.detail_subtitles), Modifier.padding(start = 8.dp))
    }
}

/** Every subtitle of one movie or episode through Bazarr: what's there, what's missing, and any other language. */
@Composable
fun MediaSubtitlesScreen(bazarrId: String, radarrId: Int?, seriesId: Int?, episodeId: Int?, title: String) {
    val c = LocalContext.current.container
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val server = remember(bazarrId) { c.servers.byId(bazarrId) }
    var media by remember { mutableStateOf<BazarrMedia?>(null) }
    var profileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var addLanguage by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BazarrSub?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (server == null || (radarrId == null && (seriesId == null || episodeId == null))) {
                MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
                return@Box
            }
            val api = remember(server) { c.bazarr(server) }
            suspend fun load() {
                runCatching {
                    val m = if (radarrId != null) api.movie(radarrId) else api.episode(episodeId!!)
                    // Episodes take their profile from the series.
                    val profileId = if (radarrId != null) m?.profileId else api.seriesProfile(seriesId!!)
                    val name = profileId?.let { id -> runCatching { api.profiles() }.getOrNull()?.firstOrNull { it.id == id }?.name }
                    Triple(m, profileId, name)
                }.onSuccess { (m, pid, name) ->
                    media = m
                    profileName = if (pid == null) null else name ?: pid.toString()
                    error = null
                }.onFailure { error = it.toTestResult() }
                loaded = true
            }
            LaunchedEffect(reload) { load() }

            fun exec(done: String, block: suspend () -> Unit) {
                scope.launch {
                    runCatching { block() }
                        .onSuccess { Toast.makeText(context, done, Toast.LENGTH_LONG).show(); load() }
                        .onFailure { Toast.makeText(context, it.message ?: "", Toast.LENGTH_LONG).show() }
                }
            }
            fun searchLanguage(code: String, name: String, hi: Boolean, forced: Boolean) = exec(context.getString(R.string.subs_searching_language, name)) {
                if (radarrId != null) api.searchMovieLanguage(radarrId, code, hi, forced)
                else api.searchEpisodeLanguage(seriesId!!, episodeId!!, code, hi, forced)
            }

            val hiText = stringResource(R.string.bazarr_hi_short)
            val forcedText = stringResource(R.string.bazarr_forced_short)
            val m = media
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
                when {
                    m == null && error != null -> MessageView(Icons.Outlined.ErrorOutline, resultText(error!!, server.type), actionLabel = stringResource(R.string.retry), onAction = { reload++ })
                    m == null && !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    m == null -> MessageView(Icons.Outlined.SubtitlesOff, stringResource(R.string.subs_not_in_bazarr))
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(vertical = 8.dp)) {
                        Group(stringResource(R.string.subs_on_disk), footer = stringResource(R.string.subs_delete_hint).takeIf { m.subtitles.any { !it.embedded } }) {
                            if (m.subtitles.isEmpty()) GroupRow(stringResource(R.string.subs_none_yet), icon = Icons.Outlined.SubtitlesOff, iconTint = MaterialTheme.colorScheme.outline)
                            m.subtitles.forEach { s ->
                                GroupRow(
                                    subLabel(s.name, s.hi, s.forced, hiText, forcedText),
                                    if (s.embedded) stringResource(R.string.subs_embedded)
                                    else listOfNotNull(s.fileName, s.size?.let { Format.bytes(it) }).joinToString(" · "),
                                    Icons.Outlined.Subtitles,
                                    onClick = if (s.embedded) null else ({ deleting = s }),
                                )
                            }
                        }
                        if (m.missing.isNotEmpty()) {
                            Group(stringResource(R.string.subs_missing), footer = stringResource(R.string.subs_missing_hint)) {
                                m.missing.forEach { l: SubLanguage ->
                                    GroupRow(
                                        subLabel(l.name.ifBlank { l.label }, l.hi, l.forced, hiText, forcedText),
                                        icon = Icons.Outlined.SubtitlesOff,
                                        iconTint = MaterialTheme.colorScheme.error,
                                        trailing = { Icon(Icons.Outlined.Search, stringResource(R.string.movie_search), tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { l.code?.let { code -> searchLanguage(code, l.name.ifBlank { l.label }, l.hi, l.forced) } },
                                    )
                                }
                            }
                        }
                        Group(stringResource(R.string.subs_get_more)) {
                            GroupRow(
                                stringResource(R.string.subs_add_language),
                                stringResource(R.string.subs_add_language_body),
                                Icons.Outlined.Add,
                                showChevron = true,
                                onClick = { addLanguage = true },
                            )
                            GroupRow(
                                stringResource(R.string.bazarr_search_manual),
                                stringResource(R.string.subs_manual_body),
                                Icons.Outlined.ManageSearch,
                                showChevron = true,
                                onClick = { nav.navigate(SubtitleSearchRoute(server.id, radarrId, seriesId, episodeId, title)) },
                            )
                            GroupRow(
                                stringResource(R.string.bazarr_languages_title),
                                listOfNotNull(
                                    profileName ?: stringResource(R.string.bazarr_no_profile),
                                    if (radarrId == null) stringResource(R.string.subs_profile_series) else null,
                                ).joinToString(" · "),
                                Icons.Outlined.Translate,
                                showChevron = true,
                                onClick = { editProfile = true },
                            )
                        }
                    }
                }
            }

            if (addLanguage) {
                LanguagePickerDialog(api, onDismiss = { addLanguage = false }) { l, hi, forced ->
                    addLanguage = false
                    searchLanguage(l.code, l.name, hi, forced)
                }
            }
            if (editProfile) {
                LanguagesProfileDialog(api, radarrId, if (radarrId == null) seriesId else null, onDismiss = { editProfile = false }) { reload++ }
            }
            deleting?.let { s ->
                AlertDialog(
                    onDismissRequest = { deleting = null },
                    title = { Text(stringResource(R.string.subs_delete_title, subLabel(s.name, s.hi, s.forced, hiText, forcedText))) },
                    text = { Text(s.fileName.orEmpty(), style = MaterialTheme.typography.bodySmall) },
                    confirmButton = {
                        TextButton(onClick = {
                            deleting = null
                            exec(context.getString(R.string.subs_deleted)) {
                                if (radarrId != null) api.deleteMovieSubtitle(radarrId, s) else api.deleteEpisodeSubtitle(seriesId!!, episodeId!!, s)
                            }
                        }) { Text(stringResource(R.string.torrent_delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

/** Pick any language Bazarr knows, optionally hearing impaired or forced. */
@Composable
private fun LanguagePickerDialog(api: BazarrApi, onDismiss: () -> Unit, onPick: (BazarrLanguage, Boolean, Boolean) -> Unit) {
    var all by remember { mutableStateOf<List<BazarrLanguage>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<BazarrLanguage?>(null) }
    var hi by remember { mutableStateOf(false) }
    var forced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { api.languages() }.onSuccess { all = it }.onFailure { error = it.message ?: "" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subs_add_language)) },
        text = {
            val list = all
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                list == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val enabled = list.filter { it.enabled }
                    val base = if (showAll || enabled.isEmpty()) list else enabled
                    val shown = base.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.code.equals(query.trim(), ignoreCase = true) }
                    if (enabled.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text(stringResource(R.string.subs_enabled_languages)) })
                            FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text(stringResource(R.string.subs_all_languages)) })
                        }
                    }
                    if (showAll || enabled.isEmpty()) {
                        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.subs_find_language)) }, modifier = Modifier.fillMaxWidth())
                    }
                    if (showAll && enabled.isNotEmpty()) {
                        Text(stringResource(R.string.subs_all_languages_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(shown.size) { i ->
                            val l = shown[i]
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = picked == l, role = Role.RadioButton) { picked = l }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = picked == l, onClick = null)
                                Text(l.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(hi, { hi = it; if (it) forced = false })
                        Text(stringResource(R.string.bazarr_hi))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(forced, { forced = it; if (it) hi = false })
                        Text(stringResource(R.string.bazarr_forced))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked != null, onClick = { picked?.let { onPick(it, hi, forced) } }) { Text(stringResource(R.string.movie_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
