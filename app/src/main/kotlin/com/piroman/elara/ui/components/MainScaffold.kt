package com.piroman.elara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.piroman.elara.ui.MainViewModel
import com.piroman.elara.ui.screens.BrowsePlaylistScreen
import com.piroman.elara.ui.screens.DownloadsScreen
import com.piroman.elara.ui.screens.HomeScreen
import com.piroman.elara.ui.screens.LibraryScreen
import com.piroman.elara.ui.screens.ListeningStatsScreen
import com.piroman.elara.ui.screens.PlayerSettingsScreen
import com.piroman.elara.ui.screens.PlaylistDetailScreen
import com.piroman.elara.ui.screens.SearchScreen
import com.piroman.elara.ui.theme.AuraScaffoldSurface
import com.piroman.elara.ui.theme.AuraTextPrimary
import com.piroman.elara.ui.theme.AuraTextSecondary
import com.piroman.elara.ui.theme.Motion

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "Home", { Icon(Icons.Default.Home, contentDescription = null) })
    object Search : Screen("search", "Search", { Icon(Icons.Default.Search, contentDescription = null) })
    object Library : Screen("library", "Library", { Icon(Icons.Default.LibraryMusic, contentDescription = null) })
}

/**
 * Hoisted out of the composable: this list is immutable, so rebuilding it on
 * every recomposition (which the old inline `listOf(...)` did) just churned the
 * NavigationBar's parameters and defeated its skipping.
 */
private val NavItems = listOf(Screen.Home, Screen.Search, Screen.Library)

/*
 * Screen transitions — a short cross-fade and nothing else.
 *
 * The NavHost originally declared none, so every tab tap and every playlist push
 * was an instant cut. The first attempt at fixing that used a fade-through with
 * a full-screen `scaleIn`/`scaleOut`, and on a real phone that traded a hard cut
 * for visible lag: scaling a whole screen forces a display-sized GPU layer that
 * is re-rasterised every frame. A plain fade over [Motion.Standard] hides the
 * cut for a fraction of the work.
 *
 * One shape for every route, deliberately. A bottom bar carries no left-to-right
 * meaning, so a directional slide would have been wrong for tabs anyway — and
 * giving the detail pushes their own slide meant two more animated full-screen
 * layers for a route that is opened rarely.
 */

private fun screenEnter(): EnterTransition =
    fadeIn(tween(Motion.Standard, easing = Motion.StandardEasing))

private fun screenExit(): ExitTransition =
    fadeOut(tween(Motion.Standard, easing = Motion.StandardEasing))

/**
 * Switches to a bottom-bar tab.
 *
 * Extracted because Home's category tiles navigate to Search as well, and these
 * three options are exactly the kind that diverge silently — a mismatch surfaces
 * much later as "back goes somewhere unexpected". One definition means a
 * category tap and a Search-tab tap produce the same back stack.
 */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MainScaffold(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isQueueOpen by viewModel.isQueueOpen.collectAsState()

    // NOTE: positionMs / durationMs are deliberately NOT collected here. They
    // tick every 400 ms during playback, and reading them in this scope
    // recomposed the entire Scaffold — NavHost, every screen and the navigation
    // bar included — 2.5 times a second, which is the app's single biggest
    // source of jank. FloatingPlayer consumes them itself, in a leaf, so a
    // position tick now invalidates one 3 dp progress bar and nothing else.

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Layer 1: the shell. Always composed, so opening the full-screen
        // player no longer throws away every screen's scroll position.
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    // Animated rather than a bare `if (currentTrack != null)`:
                    // the mini player used to pop into existence the instant the
                    // first song was chosen, shoving the navigation bar down in
                    // a single frame. It expands up from the nav bar instead.
                    AnimatedVisibility(
                        visible = currentTrack != null,
                        enter = expandVertically(
                            expandFrom = Alignment.Bottom,
                            animationSpec = tween(Motion.Standard, easing = Motion.Decelerate)
                        ) + fadeIn(tween(Motion.Standard, easing = Motion.Decelerate)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Bottom,
                            animationSpec = tween(Motion.Standard, easing = Motion.Accelerate)
                        ) + fadeOut(tween(Motion.Quick, easing = Motion.Accelerate))
                    ) {
                        FloatingPlayer(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            positionMs = viewModel.positionMs,
                            durationMs = viewModel.durationMs,
                            onTogglePlay = { viewModel.togglePlay() },
                            onNext = { viewModel.next() },
                            onExpand = { viewModel.setPlayerExpanded(true) },
                            onQueue = { viewModel.setQueueOpen(true) }
                        )
                    }
                    NavigationBar(
                        containerColor = AuraScaffoldSurface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        // Built once per bar instead of once per item.
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AuraTextPrimary,
                            selectedTextColor = AuraTextPrimary,
                            unselectedIconColor = AuraTextSecondary,
                            unselectedTextColor = AuraTextSecondary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        )
                        NavItems.forEach { screen ->
                            NavigationBarItem(
                                icon = screen.icon,
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy
                                    ?.any { it.route == screen.route } == true,
                                colors = itemColors,
                                onClick = { navController.openTab(screen.route) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                NavHost(
                    navController,
                    startDestination = Screen.Home.route,
                    Modifier.fillMaxSize(),
                    enterTransition = { screenEnter() },
                    exitTransition = { screenExit() },
                    popEnterTransition = { screenEnter() },
                    popExitTransition = { screenExit() }
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel,
                            onOpenBrowse = { navController.navigate("browse_playlist") },
                            onOpenCategory = { query ->
                                // A category tile has no feed of its own (see
                                // `HomeCategories`), so it opens Search with its own
                                // name as the query. `setSearchQuery` is the state the
                                // search pipeline already observes, so results load
                                // with no second code path.
                                viewModel.setSearchQuery(query)
                                navController.openTab(Screen.Search.route)
                            }
                        )
                    }
                    composable(Screen.Search.route) { SearchScreen(viewModel) }
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            viewModel,
                            onOpenPlaylist = { id ->
                                navController.navigate("playlist/$id")
                            },
                            onOpenDownloads = {
                                navController.navigate("downloads")
                            },
                            onOpenStats = {
                                navController.navigate("listening_stats")
                            }
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getString("playlistId")
                            ?: return@composable
                        PlaylistDetailScreen(
                            viewModel = viewModel,
                            playlistId = playlistId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("browse_playlist") {
                        BrowsePlaylistScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.clearBrowsePlaylist()
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("player_settings") {
                        PlayerSettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("listening_stats") {
                        ListeningStatsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Queue Drawer overlay.
                //
                // Composed only while the player is collapsed. The full-screen
                // player is a higher layer, so a drawer rendered here would open
                // *behind* it and look like nothing happened. When the player is
                // expanded it renders its own copy inside its own layer instead —
                // never both at once, so the panel is never drawn twice.
                if (!isPlayerExpanded) {
                    QueueDrawer(
                        viewModel = viewModel,
                        isOpen = isQueueOpen,
                        onDismiss = { viewModel.setQueueOpen(false) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── Layer 2+: the full-screen player, overlaid rather than swapped in.
        //
        // The previous Crossfade disposed the whole Scaffold while expanding, so
        // collapsing the player reset every list to the top and re-fetched the
        // screen. Overlaying keeps the shell alive underneath: opening a song is
        // now instant and coming back lands you exactly where you were.
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                animationSpec = tween(Motion.Expressive, easing = Motion.Decelerate)
            ) { it } + fadeIn(tween(Motion.Standard, easing = Motion.Decelerate)),
            exit = slideOutVertically(
                animationSpec = tween(Motion.Standard, easing = Motion.Accelerate)
            ) { it } + fadeOut(tween(Motion.Quick, easing = Motion.Accelerate))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Input shield: the player's background is opaque, but an opaque
                // background does not consume pointer events, so without this a
                // tap or fling over a gap in the player would reach the list
                // underneath and scroll it. Drawn *below* the player, so the
                // player's own controls still win.
                //
                // `detectTapGestures` rather than `clickable`: this is the same
                // idiom Material3 uses for a dialog scrim, and it avoids adding
                // an unlabelled clickable node to the accessibility tree.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } }
                )

                FullScreenPlayer(
                    viewModel = viewModel,
                    onDismiss = {
                        // Close the queue alongside the player. It renders inside
                        // the player's layer while expanded, so leaving the flag
                        // set would make it pop back into the shell on collapse.
                        viewModel.setQueueOpen(false)
                        viewModel.setPlayerExpanded(false)
                    },
                    onSettings = {
                        viewModel.setPlayerExpanded(false)
                        navController.navigate("player_settings")
                    }
                )
            }
        }
    }
}
