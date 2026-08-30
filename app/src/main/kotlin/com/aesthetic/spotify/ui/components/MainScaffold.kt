package com.aesthetic.spotify.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aesthetic.spotify.ui.MainViewModel
import com.aesthetic.spotify.ui.screens.BrowsePlaylistScreen
import com.aesthetic.spotify.ui.screens.HomeScreen
import com.aesthetic.spotify.ui.screens.LibraryScreen
import com.aesthetic.spotify.ui.screens.PlaylistDetailScreen
import com.aesthetic.spotify.ui.screens.SearchScreen

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "Home", { Icon(Icons.Default.Home, contentDescription = null) })
    object Search : Screen("search", "Search", { Icon(Icons.Default.Search, contentDescription = null) })
    object Library : Screen("library", "Library", { Icon(Icons.Default.LibraryMusic, contentDescription = null) })
}

@Composable
fun MainScaffold(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Search, Screen.Library)

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    Crossfade(
        targetState = isPlayerExpanded,
        animationSpec = tween(250),
        label = "playerExpand"
    ) { expanded ->
        if (expanded) {
            FullScreenPlayer(viewModel = viewModel, onDismiss = { viewModel.setPlayerExpanded(false) })
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        if (currentTrack != null) {
                            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                            FloatingPlayer(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                progress = progress,
                                onTogglePlay = { viewModel.togglePlay() },
                                onNext = { viewModel.next() },
                                onExpand = { viewModel.setPlayerExpanded(true) }
                            )
                        }
                        NavigationBar(
                            containerColor = Color(0xFF0B0B10),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = screen.icon,
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy
                                        ?.any { it.route == screen.route } == true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        unselectedIconColor = Color(0xFF8A8A96),
                                        unselectedTextColor = Color(0xFF8A8A96),
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                    ),
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController,
                    startDestination = Screen.Home.route,
                    Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(viewModel, onOpenBrowse = {
                            navController.navigate("browse_playlist")
                        })
                    }
                    composable(Screen.Search.route) { SearchScreen(viewModel) }
                    composable(Screen.Library.route) {
                        LibraryScreen(viewModel, onOpenPlaylist = { id ->
                            navController.navigate("playlist/$id")
                        })
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
                }
            }
        }
    }
}
