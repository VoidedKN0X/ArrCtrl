package com.vknxworks.arrctrl.ui.movies

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
import com.vknxworks.arrctrl.ui.common.ReleasesScreen
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar

/** Interactive search for one movie. */
@Composable
fun MovieReleasesScreen(serverId: String, movieId: Int, title: String) {
    val c = LocalContext.current.container
    val server = remember(serverId) { c.servers.byId(serverId) }
    if (server == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(title)
            MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.server_not_found))
        }
        return
    }
    val api = remember(server) { c.radarr(server) }
    ReleasesScreen(
        title = stringResource(R.string.release_title, title),
        type = server.type,
        load = { api.releases(movieId) },
        grab = { api.grab(it) },
    )
}
