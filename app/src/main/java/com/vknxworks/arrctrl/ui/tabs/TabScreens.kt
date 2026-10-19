package com.vknxworks.arrctrl.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.servers.icon

@Composable
private fun savedServers(): List<Server> {
    val list by LocalContext.current.container.servers.servers.collectAsStateWithLifecycle()
    return list
}

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
