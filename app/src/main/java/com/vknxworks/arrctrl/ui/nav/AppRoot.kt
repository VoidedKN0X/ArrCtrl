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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.vknxworks.arrctrl.ui.components.LocalHazeState
import com.vknxworks.arrctrl.ui.servers.ImportScreen
import com.vknxworks.arrctrl.ui.servers.ServerEditScreen
import com.vknxworks.arrctrl.ui.settings.AccountScreen
import com.vknxworks.arrctrl.ui.settings.SettingsScreen
import com.vknxworks.arrctrl.ui.activity.ActivityScreen
import com.vknxworks.arrctrl.ui.activity.AddTorrentScreen
import com.vknxworks.arrctrl.ui.activity.ManualImportScreen
import com.vknxworks.arrctrl.ui.activity.TorrentScreen
import com.vknxworks.arrctrl.ui.tabs.CalendarScreen
import com.vknxworks.arrctrl.ui.tabs.MoreScreen
import com.vknxworks.arrctrl.ui.tabs.MoviesScreen
import com.vknxworks.arrctrl.ui.tabs.SeriesScreen
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AppRoot() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MainScaffold()
    }
}

private data class TopDest(val route: Any, val item: TabItem)

@Composable
private fun topDestinations(): List<TopDest> = listOf(
    TopDest(MoviesRoute, TabItem(stringResource(R.string.tab_movies), Icons.Outlined.Movie, Icons.Filled.Movie)),
    TopDest(SeriesRoute, TabItem(stringResource(R.string.tab_series), Icons.Outlined.Tv, Icons.Filled.Tv)),
    TopDest(CalendarRoute, TabItem(stringResource(R.string.tab_calendar), Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)),
    TopDest(ActivityRoute, TabItem(stringResource(R.string.tab_activity), Icons.Outlined.Download, Icons.Filled.Download)),
    TopDest(MoreRoute, TabItem(stringResource(R.string.tab_more), Icons.Outlined.Widgets, Icons.Filled.Widgets)),
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
    val incoming by c.pendingTorrent.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val t = incoming ?: return@LaunchedEffect
        c.pendingTorrent.value = null
        navController.navigate(AddTorrentRoute(link = t.link, file = t.fileUri)) { launchSingleTop = true }
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
                                icon = { Icon(if (selected) top.item.selectedIcon else top.item.icon, contentDescription = top.item.label) },
                                label = { Text(top.item.label) },
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
 * Phones: content runs edge to edge and scrolls under a floating glass island.
 * The island shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(
    navController: NavHostController,
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val showTabs = selectedIndex >= 0
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
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
                    .padding(bottom = 12.dp),
            ) {
                GlassTabBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
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

        composable<AccountRoute> { AccountScreen() }
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
        composable<AddTorrentRoute> {
            val r = it.toRoute<AddTorrentRoute>()
            AddTorrentScreen(r.link, r.file)
        }
    }
}
