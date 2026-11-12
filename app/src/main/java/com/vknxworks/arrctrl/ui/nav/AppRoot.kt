package com.vknxworks.arrctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.container
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.CtrlBottomBar
import com.vknxworks.arrctrl.ui.components.TabItem
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.components.rememberNotificationAccess
import com.vknxworks.arrctrl.ui.lock.lockCovering
import com.vknxworks.arrctrl.ui.servers.ImportScreen
import com.vknxworks.arrctrl.ui.servers.ServerEditScreen
import com.vknxworks.arrctrl.ui.settings.YouScreen
import com.vknxworks.arrctrl.ui.settings.NotificationsScreen
import com.vknxworks.arrctrl.ui.settings.SettingsScreen
import com.vknxworks.arrctrl.ui.activity.ActivityScreen
import com.vknxworks.arrctrl.ui.activity.AddTorrentScreen
import com.vknxworks.arrctrl.ui.activity.ManualImportScreen
import com.vknxworks.arrctrl.ui.activity.TorrentScreen
import com.vknxworks.arrctrl.ui.calendar.CalendarScreen
import com.vknxworks.arrctrl.ui.tabs.MoreScreen
import com.vknxworks.arrctrl.ui.more.AutobrrScreen
import com.vknxworks.arrctrl.ui.more.BazarrScreen
import com.vknxworks.arrctrl.ui.more.CleanuparrScreen
import com.vknxworks.arrctrl.ui.more.HealthScreen
import com.vknxworks.arrctrl.ui.more.ProwlarrScreen
import com.vknxworks.arrctrl.ui.more.MediaSubtitlesScreen
import com.vknxworks.arrctrl.ui.more.SubtitleSearchScreen
import com.vknxworks.arrctrl.ui.movies.AddMovieScreen
import com.vknxworks.arrctrl.ui.movies.MovieReleasesScreen
import com.vknxworks.arrctrl.ui.movies.MovieScreen
import com.vknxworks.arrctrl.ui.movies.MoviesScreen
import com.vknxworks.arrctrl.ui.series.AddSeriesScreen
import com.vknxworks.arrctrl.ui.series.EpisodeReleasesScreen
import com.vknxworks.arrctrl.ui.series.SeasonReleasesScreen
import com.vknxworks.arrctrl.ui.series.SeasonScreen
import com.vknxworks.arrctrl.ui.series.SeriesDetailScreen
import com.vknxworks.arrctrl.ui.series.SeriesScreen
import com.vknxworks.arrctrl.ui.wanted.WantedScreen
import com.vknxworks.arrctrl.ui.music.AddArtistScreen
import com.vknxworks.arrctrl.ui.music.ArtistScreen
import com.vknxworks.arrctrl.ui.music.MusicScreen
import com.vknxworks.arrctrl.ui.links.OpenLinkScreen
import kotlinx.coroutines.flow.first

@Composable
fun AppRoot() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScaffold()
    }
}

private data class TopDest(val route: Any, val item: TabItem)

@Composable
private fun topDestinations(): List<TopDest> = listOf(
    TopDest(MoviesRoute, TabItem(stringResource(R.string.tab_movies), Icons.Outlined.Movie)),
    TopDest(SeriesRoute, TabItem(stringResource(R.string.tab_series), Icons.Outlined.Tv)),
    TopDest(CalendarRoute, TabItem(stringResource(R.string.tab_calendar), Icons.Outlined.CalendarMonth)),
    TopDest(ActivityRoute, TabItem(stringResource(R.string.tab_activity), Icons.Outlined.Download)),
    TopDest(MoreRoute, TabItem(stringResource(R.string.tab_more), Icons.Outlined.Widgets)),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val tops = topDestinations()
    val selectedIndex = tops.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(tops[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val c = LocalContext.current.container

    // Android 13+ drops notifications until they're allowed: with notifications on, ask once, as soon
    // as ArrCtrl is unlocked. After that, the Notifications screen shows it and offers the fix.
    val appSettings by c.settings.settings.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess()
    val covered = lockCovering(c.vault)
    LaunchedEffect(covered, appSettings.notifyEnabled, notificationAccess.granted) {
        if (!covered && appSettings.notifyEnabled) notificationAccess.askOnce()
    }

    // Connection status for the profile picture's "connected" badge, checked whenever the server list changes.
    val savedServers by c.servers.servers.collectAsStateWithLifecycle()
    LaunchedEffect(savedServers) { c.status.refresh(savedServers) }

    val incoming by c.pendingTorrent.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val t = incoming ?: return@LaunchedEffect
        // Opened from another app straight after start-up: wait for the saved servers first.
        c.servers.loaded.first { it }
        c.pendingTorrent.value = null
        navController.navigate(AddTorrentRoute(link = t.link, file = t.fileUri)) { launchSingleTop = true }
    }

    // A tapped notification: "activity", "health", "movie/<serverId>/<id>" or "series/<serverId>/<id>".
    val open by c.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(open) {
        val o = open ?: return@LaunchedEffect
        // A tap that started ArrCtrl: the screens look the server up by id, so wait until the servers are read.
        c.servers.loaded.first { it }
        c.pendingOpen.value = null
        val parts = o.split('/')
        when (parts.firstOrNull()) {
            "activity" -> selectTab(tops.indexOfFirst { it.route == ActivityRoute })
            "calendar" -> selectTab(tops.indexOfFirst { it.route == CalendarRoute })
            "health" -> navController.navigate(HealthRoute) { launchSingleTop = true }
            "movie" -> parts.getOrNull(2)?.toIntOrNull()?.let { id -> navController.navigate(MovieRoute(parts[1], id)) { launchSingleTop = true } }
            "series" -> parts.getOrNull(2)?.toIntOrNull()?.let { id -> navController.navigate(SeriesDetailRoute(parts[1], id)) { launchSingleTop = true } }
            "artist" -> parts.getOrNull(2)?.toIntOrNull()?.let { id -> navController.navigate(ArtistRoute(parts[1], id)) { launchSingleTop = true } }
            // An arrctrl:// link from another app, checked in MainActivity: "link/movie/<tmdb>" or "link/series/<tvdb>/<tmdb>".
            "link" -> when (parts.getOrNull(1)) {
                "movie" -> parts.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }?.let { navController.navigate(OpenLinkRoute("movie", tmdb = it)) }
                "series" -> {
                    val tvdb = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val tmdb = parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    if (tvdb > 0 || tmdb > 0) navController.navigate(OpenLinkRoute("series", tmdb = tmdb, tvdb = tvdb))
                }
                else -> Unit
            }
            else -> Unit
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        tops.forEachIndexed { i, top ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(top.item.icon, contentDescription = top.item.label) },
                                label = { Text(top.item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.Text,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, tops.map { it.item }, selectedIndex, ::selectTab)
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating Signal bottom bar.
 * The bar shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val showTabs = selectedIndex >= 0
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController)
            }
            AnimatedVisibility(
                visible = showTabs,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
            ) {
                CtrlBottomBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = MoviesRoute) {
        composable<MoviesRoute> { MoviesScreen() }
        composable<SeriesRoute> { SeriesScreen() }
        composable<CalendarRoute> { CalendarScreen() }
        composable<ActivityRoute> { ActivityScreen() }
        composable<MoreRoute> { MoreScreen() }

        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<ServerEditRoute> {
            val r = it.toRoute<ServerEditRoute>()
            ServerEditScreen(r.id, r.type)
        }
        composable<ImportRoute> { ImportScreen() }
        composable<TorrentRoute> {
            val r = it.toRoute<TorrentRoute>()
            TorrentScreen(r.serverId, r.hash)
        }
        composable<ManualImportRoute> {
            val r = it.toRoute<ManualImportRoute>()
            ManualImportScreen(r.serverId, r.downloadId, r.title)
        }
        composable<MovieRoute> {
            val r = it.toRoute<MovieRoute>()
            MovieScreen(r.serverId, r.movieId)
        }
        composable<MovieReleasesRoute> {
            val r = it.toRoute<MovieReleasesRoute>()
            MovieReleasesScreen(r.serverId, r.movieId, r.title)
        }
        composable<AddMovieRoute> {
            val r = it.toRoute<AddMovieRoute>()
            AddMovieScreen(r.serverId, r.term)
        }
        composable<SeriesDetailRoute> {
            val r = it.toRoute<SeriesDetailRoute>()
            SeriesDetailScreen(r.serverId, r.seriesId)
        }
        composable<SeasonRoute> {
            val r = it.toRoute<SeasonRoute>()
            SeasonScreen(r.serverId, r.seriesId, r.season)
        }
        composable<SeasonReleasesRoute> {
            val r = it.toRoute<SeasonReleasesRoute>()
            SeasonReleasesScreen(r.serverId, r.seriesId, r.season, r.title)
        }
        composable<EpisodeReleasesRoute> {
            val r = it.toRoute<EpisodeReleasesRoute>()
            EpisodeReleasesScreen(r.serverId, r.episodeId, r.title)
        }
        composable<AddSeriesRoute> {
            val r = it.toRoute<AddSeriesRoute>()
            AddSeriesScreen(r.serverId, r.term)
        }
        composable<ProwlarrRoute> { ProwlarrScreen() }
        composable<BazarrRoute> { BazarrScreen() }
        composable<AutobrrRoute> { AutobrrScreen() }
        composable<CleanuparrRoute> { CleanuparrScreen() }
        composable<HealthRoute> { HealthScreen() }
        composable<OpenLinkRoute> {
            val r = it.toRoute<OpenLinkRoute>()
            OpenLinkScreen(r.kind, r.tmdb, r.tvdb)
        }
        composable<MusicRoute> { MusicScreen() }
        composable<ArtistRoute> {
            val r = it.toRoute<ArtistRoute>()
            ArtistScreen(r.serverId, r.artistId)
        }
        composable<AddArtistRoute> { AddArtistScreen(it.toRoute<AddArtistRoute>().serverId) }
        composable<WantedRoute> { WantedScreen(it.toRoute<WantedRoute>().serverId) }
        composable<NotificationsRoute> { NotificationsScreen() }
        composable<MediaSubtitlesRoute> {
            val r = it.toRoute<MediaSubtitlesRoute>()
            MediaSubtitlesScreen(r.bazarrId, r.radarrId, r.seriesId, r.episodeId, r.title)
        }
        composable<SubtitleSearchRoute> {
            val r = it.toRoute<SubtitleSearchRoute>()
            SubtitleSearchScreen(r.serverId, r.radarrId, r.seriesId, r.episodeId, r.title)
        }
        composable<AddTorrentRoute> {
            val r = it.toRoute<AddTorrentRoute>()
            AddTorrentScreen(r.link, r.file)
        }
    }
}
