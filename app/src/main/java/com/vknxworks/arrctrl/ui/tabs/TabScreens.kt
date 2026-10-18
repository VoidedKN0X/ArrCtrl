package com.vknxworks.arrctrl.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.TabTitle

/** A main tab with nothing to show yet. Replaced screen by screen as each service is wired up. */
@Composable
private fun EmptyTab(title: String, icon: ImageVector, emptyTitle: String, emptyBody: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(title)
        MessageView(icon, emptyTitle, emptyBody, Modifier.padding(bottom = LocalBottomClearance.current))
    }
}

@Composable
fun MoviesScreen() = EmptyTab(
    stringResource(R.string.tab_movies),
    Icons.Outlined.Movie,
    stringResource(R.string.empty_movies_title),
    stringResource(R.string.empty_movies_body),
)

@Composable
fun SeriesScreen() = EmptyTab(
    stringResource(R.string.tab_series),
    Icons.Outlined.Tv,
    stringResource(R.string.empty_series_title),
    stringResource(R.string.empty_series_body),
)

@Composable
fun CalendarScreen() = EmptyTab(
    stringResource(R.string.tab_calendar),
    Icons.Outlined.CalendarMonth,
    stringResource(R.string.empty_calendar_title),
    stringResource(R.string.empty_calendar_body),
)

@Composable
fun ActivityScreen() = EmptyTab(
    stringResource(R.string.tab_activity),
    Icons.Outlined.Download,
    stringResource(R.string.empty_activity_title),
    stringResource(R.string.empty_activity_body),
)

/** Prowlarr, Bazarr, autobrr, Cleanuparr and system health. Each opens once its server is set up. */
@Composable
fun MoreScreen() {
    val notSetUp = stringResource(R.string.not_set_up)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_more))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp + LocalBottomClearance.current),
        ) {
            Group(stringResource(R.string.more_tools), footer = stringResource(R.string.more_footer)) {
                GroupRow(stringResource(R.string.service_prowlarr), "${stringResource(R.string.service_prowlarr_role)} · $notSetUp", Icons.Outlined.Search, enabled = false)
                GroupRow(stringResource(R.string.service_bazarr), "${stringResource(R.string.service_bazarr_role)} · $notSetUp", Icons.Outlined.Subtitles, enabled = false)
                GroupRow(stringResource(R.string.service_autobrr), "${stringResource(R.string.service_autobrr_role)} · $notSetUp", Icons.Outlined.RssFeed, enabled = false)
                GroupRow(stringResource(R.string.service_cleanuparr), "${stringResource(R.string.service_cleanuparr_role)} · $notSetUp", Icons.Outlined.CleaningServices, enabled = false)
            }
            Group {
                GroupRow(stringResource(R.string.system_health), stringResource(R.string.system_health_role), Icons.Outlined.HealthAndSafety, enabled = false)
            }
        }
    }
}
