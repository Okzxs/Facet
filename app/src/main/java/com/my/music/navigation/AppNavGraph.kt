package com.my.music.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.my.music.core.ui.components.MiniPlayer
import com.my.music.data.model.Track
import com.my.music.feature.home.HomeScreen
import com.my.music.feature.library.LibraryScreen
import com.my.music.feature.player.PlayerViewModel
import com.my.music.feature.player.PlayerScreen
import com.my.music.feature.search.SearchScreen
import com.my.music.feature.wave.WaveScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Скрываем BottomBar на экране плеера
    val showBottomBar = currentDestination?.route != Screen.Player.route

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

                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.route == item.screen.route } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        // Избегаем накопления back stack
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onTrackClick = { track, queue -> openPlayer(track, queue) })
            }
            composable(Screen.Search.route) {
                SearchScreen(onTrackClick = { track, queue -> openPlayer(track, queue) })
            }
            composable(Screen.Library.route) {
                LibraryScreen(onTrackClick = { track, queue -> openPlayer(track, queue) })
            }
            composable(Screen.Wave.route) {
                WaveScreen(onTrackClick = { navController.navigate(Screen.Player.route) })
            }
            composable(
                route = Screen.Player.route,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                },
                popEnterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
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
