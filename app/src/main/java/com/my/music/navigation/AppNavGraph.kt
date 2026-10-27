package com.my.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.my.music.core.ui.components.ExpressiveBottomDock
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

    // Скрываем BottomBar на полноэкранных экранах (плеер, настройки).
    val showBottomBar by remember {
        derivedStateOf {
            val route = navBackStackEntry?.destination?.route
            route != Screen.Player.route && route != Screen.Settings.route
        }
    }

    // Выбранная вкладка пересчитывается только при смене back stack,
    // а не на каждой рекомпозиции дока.
    val selectedRoute by remember {
        derivedStateOf {
            bottomNavItems.firstOrNull { item ->
                navBackStackEntry?.destination?.hierarchy
                    ?.any { it.route == item.screen.route } == true
            }?.screen?.route
        }
    }

    // Общий PlayerViewModel для MiniPlayer и PlayerScreen (один и тот же экземпляр через Hilt)
    val playerViewModel: PlayerViewModel = hiltViewModel()

    // Стабильные колбэки: не пересоздаются при рекомпозиции дока.
    val onTabSelect: (BottomNavItem) -> Unit = remember(navController) {
        { item ->
            navController.navigate(item.screen.route) {
                // Избегаем накопления back stack
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val onDockExpand: () -> Unit = remember(navController) {
        { navController.navigate(Screen.Player.route) }
    }

    // Открыть плеер и загрузить трек с очередью
    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        playerViewModel.playTrackWithQueue(track, queue)
        navController.navigate(Screen.Player.route)
    }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                ExpressiveBottomDock(
                    items = bottomNavItems,
                    selectedRoute = selectedRoute,
                    onSelect = onTabSelect,
                    onExpandClick = onDockExpand,
                    viewModel = playerViewModel,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { tabEnterTransition() },
            exitTransition = { tabExitTransition() },
            popEnterTransition = { tabEnterTransition() },
            popExitTransition = { tabExitTransition() },
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
                // Выход из тяжёлого плеера не анимируем: blur и большая обложка
                // при слайде дают просадку кадров, экран должен исчезать сразу.
                exitTransition = { ExitTransition.None },
                popEnterTransition = {
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(animationSpec = tween(240))
                },
                popExitTransition = { ExitTransition.None },
            ) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onFindOnYouTube = { track ->
                        playerViewModel.findOnYouTube(track)
                        navController.navigate(Screen.Search.route) {
                            // Уходим из полноэкранного плеера в поиск, сохраняя вкладки
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    viewModel = playerViewModel,
                )
            }
        }
    }
}

private const val TabTransitionMillis = 300

/**
 * Индекс вкладки в нижнем доке или -1 для полноэкранных маршрутов.
 */
private fun NavBackStackEntry.tabIndex(): Int =
    bottomNavItems.indexOfFirst { item ->
        destination.hierarchy.any { it.route == item.screen.route }
    }

/**
 * Направление перехода между вкладками: +1 — к большим индексам (вправо),
 * -1 — к меньшим, 0 — переход не между вкладками.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = initialState.tabIndex()
    val to = targetState.tabIndex()
    if (from < 0 || to < 0 || from == to) return 0
    return if (to > from) 1 else -1
}

/**
 * Вход вкладки: выезжает с той стороны, куда движется пользователь.
 * Для полноэкранных маршрутов и возврата с них — только мягкое проявление.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    val direction = tabDirection()
    if (direction == 0) {
        return fadeIn(animationSpec = tween(180))
    }
    return slideInHorizontally(
        initialOffsetX = { width -> direction * width / 3 },
        animationSpec = tween(
            durationMillis = TabTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = TabTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    )
}

/**
 * Выход вкладки: уезжает в сторону, противоположную входу новой.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(): ExitTransition {
    val direction = tabDirection()
    if (direction == 0) {
        return fadeOut(animationSpec = tween(120))
    }
    return slideOutHorizontally(
        targetOffsetX = { width -> -direction * width / 3 },
        animationSpec = tween(
            durationMillis = TabTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = TabTransitionMillis,
            easing = FastOutSlowInEasing,
        ),
    )
}
