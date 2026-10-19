package com.vknxworks.arrctrl.ui.more

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.theme.Gutter

/**
 * Frame for a More tool: top bar, a chip per server when there's more than one of [type], and [content]
 * for the chosen server. Without a server it offers to add one. The content is reset when switching server.
 */
@Composable
fun ToolScaffold(
    title: String,
    type: ServiceType,
    actions: @Composable RowScope.(Server) -> Unit = {},
    content: @Composable (Server) -> Unit,
) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val all by c.servers.servers.collectAsStateWithLifecycle()
    val servers = all.filter { it.type == type }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val server = servers.firstOrNull { it.id == chosen } ?: servers.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title) { if (server != null) actions(server) }
        if (server == null) {
            MessageView(
                Icons.Outlined.CloudOff,
                stringResource(R.string.tool_no_server, stringResource(type.label)),
                actionLabel = stringResource(R.string.add_service, stringResource(type.label)),
                onAction = { nav.navigate(ServerEditRoute(type = type.id)) },
            )
            return@Column
        }
        if (servers.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { s -> FilterChip(selected = s.id == server.id, onClick = { chosen = s.id }, label = { Text(s.name) }) }
            }
        }
        key(server.id) { content(server) }
    }
}

/** Small number tile for stats rows. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
