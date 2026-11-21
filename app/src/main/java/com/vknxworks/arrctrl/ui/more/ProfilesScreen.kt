package com.vknxworks.arrctrl.ui.more

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.ArrProfilesApi
import com.vknxworks.arrctrl.data.api.ProfileEdits
import com.vknxworks.arrctrl.data.api.ProfileEdits.with
import com.vknxworks.arrctrl.data.api.ProfileProblem
import com.vknxworks.arrctrl.data.api.ServerRefusedException
import com.vknxworks.arrctrl.data.api.arr
import com.vknxworks.arrctrl.data.api.bool
import com.vknxworks.arrctrl.data.api.int
import com.vknxworks.arrctrl.data.api.objects
import com.vknxworks.arrctrl.data.api.str
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.ui.components.CtrlAlertDialog
import com.vknxworks.arrctrl.ui.components.CtrlFilterChip
import com.vknxworks.arrctrl.ui.components.CtrlPrimaryButton
import com.vknxworks.arrctrl.ui.components.CtrlSwitch
import com.vknxworks.arrctrl.ui.components.CtrlTextField
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.topDivider
import com.vknxworks.arrctrl.ui.servers.failureText
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.CtrlType
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private enum class ProfilesTab(val label: Int) {
    QUALITY(R.string.prof_quality_profiles),
    FORMATS(R.string.prof_custom_formats),
}

/**
 * Quality profiles and custom formats of one Radarr, Sonarr or Lidarr server: which qualities a profile
 * allows and where it stops upgrading, its custom format scores, and the custom formats themselves with
 * their conditions.
 */
@Composable
fun ProfilesScreen() {
    val c = LocalContext.current.container
    val all by c.servers.servers.collectAsStateWithLifecycle()
    val servers = all.filter { it.type in ArrProfilesApi.TYPES }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(ProfilesTab.QUALITY) }
    val server = servers.firstOrNull { it.id == chosen } ?: servers.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.prof_title))
        if (server == null) {
            MessageView(Icons.Outlined.CloudOff, stringResource(R.string.prof_no_servers), stringResource(R.string.prof_no_servers_body))
            return@Column
        }
        if (servers.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s -> CtrlFilterChip(selected = s.id == server.id, onClick = { chosen = s.id }, label = { Text(s.name) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfilesTab.entries.forEach { t -> CtrlFilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(stringResource(t.label)) }) }
        }
        key(server.id, tab) {
            when (tab) {
                ProfilesTab.QUALITY -> QualityProfilesTab(server)
                ProfilesTab.FORMATS -> CustomFormatsTab(server)
            }
        }
    }
}

/** What a failed save says: the server's own reasons when it gave them, otherwise the usual connection text. */
private fun saveFailure(context: android.content.Context, e: Throwable): String =
    if (e is ServerRefusedException) context.getString(R.string.prof_refused, e.reasons.joinToString("\n")) else failureText(context, e)

@Composable
private fun ListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter).topDivider().heightIn(min = 52.dp).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetTitle(title: String, saving: Boolean, canSave: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (saving) CircularProgressIndicator(Modifier.padding(end = 12.dp).width(20.dp), strokeWidth = 2.dp)
        else TextButton(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.prof_save)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        CtrlSwitch(checked, onCheckedChange = null)
    }
}

/** A whole-number field that keeps what's typed (also "-" on the way to "-50") and reports every valid number. */
@Composable
private fun IntField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    CtrlTextField(
        value = text,
        onValueChange = { t ->
            text = t.take(8)
            text.trim().toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        isError = text.trim().toIntOrNull() == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = CtrlType.Label, color = CtrlColors.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

// ---------- Quality profiles ----------

@Composable
private fun QualityProfilesTab(server: Server) {
    val context = LocalContext.current
    val c = context.container
    val api = remember(server) { ArrProfilesApi(c.http, server) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JsonObject>?>(null) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun load() {
        try {
            list = api.qualityProfiles()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            error?.let { e -> item { ServerErrorCard(server, e) } }
            val profiles = list
            if (profiles == null && error == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (profiles != null) {
                items(profiles, key = { it.int("id") ?: it.hashCode() }) { p ->
                    val rows = ProfileEdits.qualityRows(p)
                    val cutoff = rows.firstOrNull { it.cutoffId == p.int("cutoff") }?.name
                    ListRow(
                        p.str("name").orEmpty(),
                        listOfNotNull(
                            pluralStringResource(R.plurals.prof_allowed_count, rows.count { it.allowed }, rows.count { it.allowed }),
                            if (p.bool("upgradeAllowed") == true && cutoff != null) stringResource(R.string.prof_upgrade_until_x, cutoff) else stringResource(R.string.prof_no_upgrades),
                        ).joinToString(" · "),
                    ) { editing = p }
                }
                item { Hint(stringResource(R.string.prof_quality_note)) }
            }
        }
    }

    editing?.let { original ->
        QualityProfileSheet(
            original,
            onDismiss = { editing = null },
            save = { changed -> api.saveQualityProfile(changed) },
            onSaved = {
                editing = null
                Toast.makeText(context, R.string.prof_saved, Toast.LENGTH_SHORT).show()
                scope.launch { load() }
            },
        )
    }
}

@Composable
private fun QualityProfileSheet(original: JsonObject, onDismiss: () -> Unit, save: suspend (JsonObject) -> JsonObject, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(original) }
    var saving by remember { mutableStateOf(false) }
    val rows = ProfileEdits.qualityRows(draft)
    val problem = ProfileEdits.problem(draft)
    val upgrades = draft.bool("upgradeAllowed") == true
    val scores = ProfileEdits.formatScores(draft)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CtrlColors.Surface,
        shape = CtrlShapes.Sheet,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(original.str("name").orEmpty(), saving, canSave = problem == null && draft != original) {
            saving = true
            scope.launch {
                try {
                    save(draft)
                    onSaved()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Toast.makeText(context, saveFailure(context, e), Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "name") {
                CtrlTextField(
                    value = draft.str("name").orEmpty(),
                    onValueChange = { draft = draft.with("name", it) },
                    label = { Text(stringResource(R.string.prof_name)) },
                    singleLine = true,
                    isError = problem == ProfileProblem.NAME_BLANK,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            item(key = "upgrades") {
                SwitchRow(stringResource(R.string.prof_upgrades), stringResource(R.string.prof_upgrades_sub), upgrades) { draft = draft.with("upgradeAllowed", it) }
            }
            item(key = "q-head") {
                SectionLabel(stringResource(R.string.prof_qualities))
                Hint(stringResource(R.string.prof_qualities_hint))
                when (problem) {
                    ProfileProblem.NOTHING_ALLOWED -> Hint(stringResource(R.string.prof_problem_none))
                    ProfileProblem.CUTOFF_NOT_ALLOWED -> Hint(stringResource(R.string.prof_problem_cutoff))
                    else -> Unit
                }
            }
            // Best quality first, as on the server's own page (it keeps them lowest first).
            items(rows.reversed(), key = { "q-" + it.index }) { row ->
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { draft = ProfileEdits.setAllowed(draft, row.index, !row.allowed) }.padding(vertical = 6.dp)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.members.isNotEmpty()) {
                            Text(row.members.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    RadioButton(
                        selected = row.cutoffId == draft.int("cutoff"),
                        onClick = { draft = ProfileEdits.setCutoff(draft, row.cutoffId) },
                        enabled = row.allowed,
                    )
                    CtrlSwitch(row.allowed, onCheckedChange = { draft = ProfileEdits.setAllowed(draft, row.index, it) })
                }
            }
            item(key = "s-head") {
                SectionLabel(stringResource(R.string.prof_format_scores))
                Hint(stringResource(R.string.prof_format_scores_hint))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField(stringResource(R.string.prof_min_score), draft.int("minFormatScore") ?: 0, Modifier.weight(1f)) { draft = draft.with("minFormatScore", it) }
                    IntField(stringResource(R.string.prof_cutoff_score), draft.int("cutoffFormatScore") ?: 0, Modifier.weight(1f)) { draft = draft.with("cutoffFormatScore", it) }
                }
                // Radarr and Sonarr only; Lidarr's profiles don't have it.
                if (draft.containsKey("minUpgradeFormatScore")) {
                    IntField(
                        stringResource(R.string.prof_min_upgrade_score),
                        draft.int("minUpgradeFormatScore") ?: 1,
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    ) { draft = draft.with("minUpgradeFormatScore", it) }
                }
                if (scores.isEmpty()) Hint(stringResource(R.string.prof_no_formats))
            }
            items(scores, key = { "s-" + it.formatId }) { s ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    IntField(stringResource(R.string.prof_score), s.score, Modifier.width(120.dp)) { draft = ProfileEdits.setFormatScore(draft, s.formatId, it) }
                }
            }
        }
    }
}

// ---------- Custom formats ----------

@Composable
private fun CustomFormatsTab(server: Server) {
    val context = LocalContext.current
    val c = context.container
    val api = remember(server) { ArrProfilesApi(c.http, server) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<JsonObject>?>(null) }
    var schema by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<TestResult?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun load() {
        try {
            list = api.customFormats()
            error = null
            // The kinds of condition that can be added; without them existing ones can still be edited.
            schema = runCatching { api.customFormatSchema() }.getOrDefault(schema)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.toTestResult()
        }
    }
    LaunchedEffect(Unit) { load() }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            error?.let { e -> item { ServerErrorCard(server, e) } }
            val formats = list
            if (formats == null && error == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (formats != null) {
                item {
                    CtrlPrimaryButton(
                        stringResource(R.string.prof_new_format),
                        onClick = { editing = ProfileEdits.newFormat() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                        icon = Icons.Outlined.Add,
                    )
                }
                if (formats.isEmpty()) item { Hint(stringResource(R.string.prof_formats_none)) }
                items(formats, key = { it.int("id") ?: it.hashCode() }) { f ->
                    val specs = ProfileEdits.specs(f)
                    ListRow(
                        f.str("name").orEmpty(),
                        pluralStringResource(R.plurals.prof_condition_count, specs.size, specs.size) +
                            specs.mapNotNull { it.str("name") }.take(4).joinToString(", ").let { if (it.isEmpty()) "" else " · $it" },
                    ) { editing = f }
                }
            }
        }
    }

    editing?.let { original ->
        CustomFormatSheet(
            original,
            schema,
            onDismiss = { editing = null },
            save = { changed -> api.saveCustomFormat(changed) },
            delete = { id -> api.deleteCustomFormat(id) },
            onDone = { message ->
                editing = null
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                scope.launch { load() }
            },
        )
    }
}

@Composable
private fun CustomFormatSheet(
    original: JsonObject,
    schema: List<JsonObject>,
    onDismiss: () -> Unit,
    save: suspend (JsonObject) -> JsonObject,
    delete: suspend (Int) -> Unit,
    onDone: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(original) }
    var saving by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // The field whose options are being picked: condition index and field name.
    var picking by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val id = original.int("id")?.takeIf { it > 0 }
    val specs = ProfileEdits.specs(draft)
    val nameOk = !draft.str("name").isNullOrBlank()
    val canSave = nameOk && specs.isNotEmpty() && specs.none { it.str("name").isNullOrBlank() } && draft != original

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CtrlColors.Surface,
        shape = CtrlShapes.Sheet,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(if (id == null) stringResource(R.string.prof_new_format) else original.str("name").orEmpty(), saving, canSave) {
            saving = true
            scope.launch {
                try {
                    save(draft)
                    onDone(R.string.prof_saved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Toast.makeText(context, saveFailure(context, e), Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "name") {
                CtrlTextField(
                    value = draft.str("name").orEmpty(),
                    onValueChange = { draft = draft.with("name", it) },
                    label = { Text(stringResource(R.string.prof_name)) },
                    singleLine = true,
                    isError = !nameOk,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
                SwitchRow(
                    stringResource(R.string.prof_include_renaming),
                    stringResource(R.string.prof_include_renaming_sub),
                    draft.bool("includeCustomFormatWhenRenaming") == true,
                ) { draft = draft.with("includeCustomFormatWhenRenaming", it) }
                SectionLabel(stringResource(R.string.prof_conditions))
                Hint(stringResource(if (specs.isEmpty()) R.string.prof_conditions_none else R.string.prof_conditions_hint))
            }
            itemsIndexed(specs) { index, spec ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).topDivider().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            spec.str("implementationName") ?: spec.str("implementation").orEmpty(),
                            style = CtrlType.Label,
                            color = CtrlColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { draft = ProfileEdits.removeSpec(draft, index) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.prof_remove_condition))
                        }
                    }
                    CtrlTextField(
                        value = spec.str("name").orEmpty(),
                        onValueChange = { v -> draft = ProfileEdits.setSpec(draft, index) { it.with("name", v) } },
                        label = { Text(stringResource(R.string.prof_condition_name)) },
                        singleLine = true,
                        isError = spec.str("name").isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ProfileEdits.fields(spec).forEach { field ->
                        SpecField(
                            field,
                            onValue = { v -> draft = ProfileEdits.setSpec(draft, index) { ProfileEdits.setField(it, field.str("name").orEmpty(), v) } },
                            onPick = { picking = index to field.str("name").orEmpty() },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.weight(1f).clickable { draft = ProfileEdits.setSpec(draft, index) { it.with("required", spec.bool("required") != true) } }, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.prof_required), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            CtrlSwitch(spec.bool("required") == true, onCheckedChange = null)
                        }
                        Row(Modifier.weight(1f).clickable { draft = ProfileEdits.setSpec(draft, index) { it.with("negate", spec.bool("negate") != true) } }, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.prof_negate), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            CtrlSwitch(spec.bool("negate") == true, onCheckedChange = null)
                        }
                    }
                }
            }
            item(key = "add") {
                if (schema.isNotEmpty()) {
                    TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.prof_add_condition)) }
                }
                if (id != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !saving, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.prof_delete_format), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (adding) {
        PickDialog(
            stringResource(R.string.prof_add_condition),
            schema.map { it.str("implementationName") ?: it.str("implementation").orEmpty() },
            selected = -1,
            onDismiss = { adding = false },
        ) { i ->
            adding = false
            draft = ProfileEdits.addSpec(draft, schema[i])
        }
    }

    picking?.let { (specIndex, fieldName) ->
        val field = specs.getOrNull(specIndex)?.let { ProfileEdits.fields(it) }?.firstOrNull { it.str("name") == fieldName }
        val options = field.arr("selectOptions").objects()
        if (field == null || options.isEmpty()) {
            picking = null
        } else {
            val current = (field["value"] as? JsonPrimitive)?.contentOrNull
            PickDialog(
                field.str("label") ?: fieldName,
                options.map { it.str("name").orEmpty() },
                selected = options.indexOfFirst { (it["value"] as? JsonPrimitive)?.contentOrNull == current },
                onDismiss = { picking = null },
            ) { i ->
                picking = null
                val value: JsonElement = options[i]["value"] ?: JsonNull
                draft = ProfileEdits.setSpec(draft, specIndex) { ProfileEdits.setField(it, fieldName, value) }
            }
        }
    }

    if (confirmDelete && id != null) {
        CtrlAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.prof_delete_format_title)) },
            text = { Text(stringResource(R.string.prof_delete_format_body, original.str("name").orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    saving = true
                    scope.launch {
                        try {
                            delete(id)
                            onDone(R.string.prof_deleted)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Toast.makeText(context, failureText(context, e), Toast.LENGTH_LONG).show()
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(stringResource(R.string.prof_delete_format), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            shielded = true,
        )
    }
}

/** One setting of a condition, by the kind of field the server says it is. Kinds the app doesn't know are shown, not edited. */
@Composable
private fun SpecField(field: JsonObject, onValue: (JsonElement) -> Unit, onPick: () -> Unit) {
    val label = field.str("label") ?: field.str("name").orEmpty()
    val value = field["value"] as? JsonPrimitive
    val unit = field.str("unit")
    when (field.str("type")) {
        "textbox" -> CtrlTextField(
            value = value?.contentOrNull.orEmpty(),
            onValueChange = { onValue(JsonPrimitive(it)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        "number" -> {
            val isFloat = field.bool("isFloat") == true
            var text by remember { mutableStateOf(value?.contentOrNull.orEmpty()) }
            CtrlTextField(
                value = text,
                onValueChange = { t ->
                    text = t.take(16)
                    ProfileEdits.number(text, isFloat)?.let(onValue)
                },
                label = { Text(if (unit != null) "$label ($unit)" else label) },
                isError = ProfileEdits.number(text, isFloat) == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (isFloat) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        "checkbox" -> {
            val on = field.bool("value") == true
            Row(Modifier.fillMaxWidth().clickable { onValue(JsonPrimitive(!on)) }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                CtrlSwitch(on, onCheckedChange = null)
            }
        }
        "select" -> {
            val options = field.arr("selectOptions").objects()
            val current = options.firstOrNull { (it["value"] as? JsonPrimitive)?.contentOrNull == value?.contentOrNull }?.str("name")
            Row(Modifier.fillMaxWidth().clickable(enabled = options.isNotEmpty(), onClick = onPick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(current ?: value?.contentOrNull ?: stringResource(R.string.prof_choose), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.AccentText)
            }
        }
        else -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value?.contentOrNull ?: "–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    field.str("helpText")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** A list to pick one of [options] from; [selected] is marked. */
@Composable
private fun PickDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (i == selected) CtrlColors.AccentText else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
