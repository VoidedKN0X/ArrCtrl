package com.vknxworks.arrctrl.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.nav.AccountRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.servers.icon

@Composable
private fun savedServers(): List<Server> {
    val list by LocalContext.current.container.servers.servers.collectAsStateWithLifecycle()
    return list
}

/**
 * A main tab whose content arrives in a later update. Without a matching server it offers to add one;
 * with one it says the server is connected and what comes next.
 */
@Composable
private fun PendingTab(
    title: String,
    icon: ImageVector,
    needs: List<ServiceType>,
    emptyTitle: String,
    emptyBody: String,
    readyBody: String,
) {
    val nav = LocalNavigator.current
    val servers = savedServers()
    val have = servers.any { it.type in needs }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(title)
        val modifier = Modifier.padding(bottom = LocalBottomClearance.current)
        if (have) {
            MessageView(icon, stringResource(R.string.tab_connected_title), readyBody, modifier)
        } else {
            val single = needs.singleOrNull()
            MessageView(
                icon,
                emptyTitle,
                emptyBody,
                modifier,
                actionLabel = if (single != null) stringResource(R.string.add_service, stringResource(single.label)) else stringResource(R.string.add_server),
                onAction = { if (single != null) nav.navigate(ServerEditRoute(type = single.id)) else nav.navigate(AccountRoute) },
            )
        }
    }
}

@Composable
fun SeriesScreen() = PendingTab(
    stringResource(R.string.tab_series),
    Icons.Outlined.Tv,
    listOf(ServiceType.SONARR),
    stringResource(R.string.empty_series_title),
    stringResource(R.string.empty_series_body),
    stringResource(R.string.ready_series_body),
)

@Composable
fun CalendarScreen() = PendingTab(
    stringResource(R.string.tab_calendar),
    Icons.Outlined.CalendarMonth,
    listOf(ServiceType.RADARR, ServiceType.SONARR),
    stringResource(R.string.empty_calendar_title),
    stringResource(R.string.empty_calendar_body),
    stringResource(R.string.ready_calendar_body),
)

/** Prowlarr, Bazarr, autobrr, Cleanuparr and system health. Each opens in a later update. */
@Composable
fun MoreScreen() {
    val nav = LocalNavigator.current
    val servers = savedServers()
    val tools = listOf(ServiceType.PROWLARR, ServiceType.BAZARR, ServiceType.AUTOBRR, ServiceType.CLEANUPARR)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_more))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp + LocalBottomClearance.current),
        ) {
            Group(stringResource(R.string.more_tools), footer = stringResource(R.string.more_footer)) {
                tools.forEach { t ->
                    val count = servers.count { it.type == t }
                    val state = if (count == 0) stringResource(R.string.not_set_up) else stringResource(R.string.more_connected)
                    GroupRow(
                        stringResource(t.label),
                        "${stringResource(t.role)} · $state",
                        t.icon,
                        showChevron = count == 0,
                        onClick = if (count == 0) ({ nav.navigate(ServerEditRoute(type = t.id)) }) else null,
                    )
                }
            }
            Group {
                GroupRow(
                    stringResource(R.string.system_health),
                    stringResource(R.string.system_health_role),
                    Icons.Outlined.HealthAndSafety,
                    enabled = false,
                )
            }
        }
    }
}
