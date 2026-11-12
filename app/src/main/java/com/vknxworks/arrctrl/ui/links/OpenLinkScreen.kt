package com.vknxworks.arrctrl.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.Group
import com.vknxworks.arrctrl.ui.components.GroupRow
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ScreenTopBar
import com.vknxworks.arrctrl.ui.nav.AddMovieRoute
import com.vknxworks.arrctrl.ui.nav.AddSeriesRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieRoute
import com.vknxworks.arrctrl.ui.nav.OpenLinkRoute
import com.vknxworks.arrctrl.ui.nav.SeriesDetailRoute
import com.vknxworks.arrctrl.ui.nav.ServerEditRoute
import com.vknxworks.arrctrl.ui.servers.icon
import com.vknxworks.arrctrl.ui.servers.resultText
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where an arrctrl:// link lands: looks for the movie (by TMDB id) or series (by TVDB, then TMDB id) on every
 * Radarr or Sonarr server and opens it. When no server has it, opens Add movie / Add series looked up by that id;
 * with more than one server you pick which one to add it to.
 */
@Composable
fun OpenLinkScreen(kind: String, tmdb: Int, tvdb: Int) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val movie = kind == "movie"
    val type = if (movie) ServiceType.RADARR else ServiceType.SONARR
    val servers = remember { c.servers.servers.value.filter { it.type == type } }
    var choose by remember { mutableStateOf<List<Server>?>(null) }
    var failure by remember { mutableStateOf<Pair<Server, TestResult>?>(null) }
    val title = stringResource(if (movie) R.string.link_opening_movie else R.string.link_opening_series)

    /** Replaces this screen with [route], so back goes to where you were before the link. */
    fun go(route: Any) {
        nav.nav.navigate(route) {
            popUpTo<OpenLinkRoute> { inclusive = true }
            launchSingleTop = true
        }
    }

    val term = if (movie) "tmdb:$tmdb" else if (tvdb > 0) "tvdb:$tvdb" else "tmdb:$tmdb"
    fun add(s: Server) = go(if (movie) AddMovieRoute(s.id, term) else AddSeriesRoute(s.id, term))

    LaunchedEffect(Unit) {
        if (servers.isEmpty()) return@LaunchedEffect
        var lastFailure: Pair<Server, TestResult>? = null
        for (s in servers) {
            try {
                if (movie) {
                    c.radarr(s).byTmdb(tmdb)?.let { go(MovieRoute(s.id, it.id)); return@LaunchedEffect }
                } else {
                    val api = c.sonarr(s)
                    val found = (if (tvdb > 0) api.byTvdb(tvdb) else null) ?: (if (tmdb > 0) api.byTmdb(tmdb) else null)
                    found?.let { go(SeriesDetailRoute(s.id, it.id)); return@LaunchedEffect }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastFailure = s to e.toTestResult()
            }
        }
        when {
            // Every server failed: say why instead of offering to add something that may already be there.
            lastFailure != null && servers.size == 1 -> failure = lastFailure
            servers.size == 1 -> add(servers[0])
            else -> choose = servers
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title)
        val list = choose
        val f = failure
        when {
            servers.isEmpty() -> MessageView(
                Icons.Outlined.CloudOff,
                stringResource(R.string.tool_no_server, stringResource(type.label)),
                actionLabel = stringResource(R.string.add_service, stringResource(type.label)),
                onAction = { go(ServerEditRoute(type = type.id)) },
            )
            f != null -> MessageView(Icons.Outlined.ErrorOutline, f.first.name, resultText(f.second, f.first.type))
            list != null -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (movie) R.string.link_not_in_radarr else R.string.link_not_in_sonarr),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Group(stringResource(R.string.link_add_to)) {
                    list.forEach { s -> GroupRow(s.name, s.url, s.type.icon, showChevron = true, onClick = { add(s) }) }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
