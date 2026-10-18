package com.vknxworks.arrctrl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.BuildConfig
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.settings.AppSettings
import com.vknxworks.arrctrl.data.settings.ThemeChoice
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupChoice
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.GroupSwitch
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.SettingsRoute
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/VoidedKN0X/ArrCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/ArrCtrl/issues"
private const val SPONSORS_URL = "https://github.com/sponsors/VoidedKN0X"

/** Opened from the profile picture: servers, settings and about. */
@Composable
fun AccountScreen() {
    val nav = LocalNavigator.current
    val uri = LocalUriHandler.current
    val notSetUp = stringResource(R.string.not_set_up)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.account_and_settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Tune, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }

            Group(stringResource(R.string.servers), footer = stringResource(R.string.servers_footer)) {
                ServiceRow(R.string.service_radarr, R.string.service_radarr_role, Icons.Outlined.Movie, notSetUp)
                ServiceRow(R.string.service_sonarr, R.string.service_sonarr_role, Icons.Outlined.Tv, notSetUp)
                ServiceRow(R.string.service_qbittorrent, R.string.service_qbittorrent_role, Icons.Outlined.Download, notSetUp)
                ServiceRow(R.string.service_prowlarr, R.string.service_prowlarr_role, Icons.Outlined.Search, notSetUp)
                ServiceRow(R.string.service_bazarr, R.string.service_bazarr_role, Icons.Outlined.Subtitles, notSetUp)
                ServiceRow(R.string.service_autobrr, R.string.service_autobrr_role, Icons.Outlined.RssFeed, notSetUp)
                ServiceRow(R.string.service_cleanuparr, R.string.service_cleanuparr_role, Icons.Outlined.CleaningServices, notSetUp)
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = { nav.navigate(SettingsRoute) },
                )
            }

            Group(stringResource(R.string.about)) {
                GroupRow(stringResource(R.string.support), stringResource(R.string.support_subtitle), Icons.Outlined.FavoriteBorder, showChevron = true, onClick = { uri.openUri(SPONSORS_URL) })
                GroupRow(stringResource(R.string.source_code), stringResource(R.string.source_code_subtitle), Icons.Outlined.Code, showChevron = true, onClick = { uri.openUri(REPO_URL) })
                GroupRow(stringResource(R.string.report_bug), stringResource(R.string.report_bug_subtitle), Icons.Outlined.BugReport, showChevron = true, onClick = { uri.openUri(ISSUES_URL) })
            }

            Text(
                stringResource(R.string.version_line, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ServiceRow(name: Int, role: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, status: String) {
    GroupRow(stringResource(name), "${stringResource(role)} · $status", icon, enabled = false)
}

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.appearance)) {
                GroupChoice(
                    stringResource(R.string.theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                    Icons.Outlined.Palette,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(
                    stringResource(R.string.dynamic_colour),
                    stringResource(R.string.dynamic_colour_subtitle),
                    checked = s.dynamicColor,
                ) { v -> update { it.copy(dynamicColor = v) } }
            }
        }
    }
}
