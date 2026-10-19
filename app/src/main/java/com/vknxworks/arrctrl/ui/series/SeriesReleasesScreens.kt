package com.vknxworks.arrctrl.ui.series

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.ui.common.ReleasesScreen
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar

@Composable
private fun withServer(serverId: String, title: String, content: @Composable (Server) -> Unit) {
    val c = LocalContext.current.container
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(title)
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
    } else content(server)
}

/** Interactive search for a whole season: season packs and single episodes. */
@Composable
fun SeasonReleasesScreen(serverId: String, seriesId: Int, season: Int, title: String) = withServer(serverId, title) { server ->
    val api = LocalContext.current.container.sonarr(server)
    ReleasesScreen(
        title = stringResource(R.string.release_title, title),
        type = server.type,
        load = { api.seasonReleases(seriesId, season) },
        grab = { api.grab(it) },
    )
}

/** Interactive search for one episode. */
@Composable
fun EpisodeReleasesScreen(serverId: String, episodeId: Int, title: String) = withServer(serverId, title) { server ->
    val api = LocalContext.current.container.sonarr(server)
    ReleasesScreen(
        title = stringResource(R.string.release_title, title),
        type = server.type,
        load = { api.episodeReleases(episodeId) },
        grab = { api.grab(it) },
    )
}
