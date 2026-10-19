package com.my.music.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.my.music.core.ui.components.ExpressiveNavigationBar
import com.my.music.core.ui.components.MiniPlayer
import com.my.music.data.model.Track
import com.my.music.feature.home.HomeScreen
import com.my.music.feature.library.LibraryScreen
import com.my.music.feature.player.PlayerViewModel
import com.my.music.feature.player.PlayerScreen
import com.my.music.feature.search.SearchScreen
import com.my.music.feature.settings.SettingsScreen
import com.my.music.feature.wave.WaveScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Скрываем BottomBar на полноэкранных экранах (плеер, настройки)
    val showBottomBar = currentDestination?.route != Screen.Player.route &&
        currentDestination?.route != Screen.Settings.route

    // Общий PlayerViewModel для MiniPlayer и PlayerScreen (один и тот же экземпляр через Hilt)
    val playerViewModel: PlayerViewModel = hiltViewModel()

    // Открыть плеер и загрузить трек с очередью
    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        playerViewModel.playTrackWithQueue(track, queue)
        navController.navigate(Screen.Player.route)
    }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                androidx.compose.foundation.layout.Column {
                    // MiniPlayer над BottomNavigationBar
                    MiniPlayer(
                        onExpandClick = { navController.navigate(Screen.Player.route) },
                        viewModel = playerViewModel,
                    )

                    val selectedRoute = bottomNavItems.firstOrNull { item ->
                        currentDestination?.hierarchy
                            ?.any { it.route == item.screen.route } == true
                    }?.screen?.route

                    ExpressiveNavigationBar(
                        items = bottomNavItems,
                        selectedRoute = selectedRoute,
                        onSelect = { item ->
                            navController.navigate(item.screen.route) {
                                // Избегаем накопления back stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    scaleIn(
                        initialScale = 0.97f,
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
            },
            exitTransition = { fadeOut(animationSpec = tween(160)) },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    scaleIn(
                        initialScale = 0.97f,
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
            },
            popExitTransition = { fadeOut(animationSpec = tween(160)) },
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onTrackClick = { track, queue -> openPlayer(track, queue) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(onTrackClick = { track, queue -> openPlayer(track, queue) })
            }
            composable(Screen.Library.route) {
                LibraryScreen(onTrackClick = { track, queue -> openPlayer(track, queue) })
            }
            composable(Screen.Wave.route) {
                WaveScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Player.route,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(animationSpec = tween(240))
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(animationSpec = tween(240))
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeOut(animationSpec = tween(180))
                },
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = playerViewModel,
                )
            }
        }
    }
}
