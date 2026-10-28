package com.my.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.my.music.core.ui.components.ExpressiveBottomDock
import com.my.music.core.ui.theme.prewarmPlayerPalette
import com.my.music.data.model.Track
import com.my.music.feature.home.HomeScreen
import com.my.music.feature.library.LibraryScreen
import com.my.music.feature.player.PlayerViewModel
import com.my.music.feature.player.PlayerScreen
import com.my.music.feature.search.SearchScreen
import com.my.music.feature.settings.SettingsScreen
import com.my.music.feature.wave.WaveScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Плеер — оверлей поверх NavHost, а не отдельный маршрут. Так вкладка
    // остаётся скомпонованной под ним и реально проглядывает при свайпе вниз.
    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }

    // Скрываем BottomBar на полноэкранных экранах (плеер, настройки).
    val showBottomBar by remember {
        derivedStateOf {
            val route = navBackStackEntry?.destination?.route
            !isPlayerOpen && route != Screen.Settings.route
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
    val onDockExpand: () -> Unit = remember {
        { isPlayerOpen = true }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Открыть плеер и загрузить трек с очередью
    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        // Прогреваем палитру обложки заранее — к открытию плеера цвет уже в кэше,
        // поэтому фон не пересчитывается и не анимируется прямо во время перехода.
        scope.launch { prewarmPlayerPalette(context, track.artworkUrl) }
        playerViewModel.playTrackWithQueue(track, queue)
        isPlayerOpen = true
    }

    // Измеренная высота плавающего дока (вместе с его navigationBarsPadding).
    // Экраны-вкладки добавляют её в нижний contentPadding, чтобы контент не
    // прятался под доком. NavHost при этом остаётся во весь экран и НЕ меняет
    // размер при показе/скрытии дока — отсюда и плавность перехода в плеер.
    val density = LocalDensity.current
    var dockHeightPx by remember { mutableStateOf(0) }
    val dockHeight = with(density) { dockHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Непрозрачный фон под NavHost: во время перехода вкладок оба экрана
            // полупрозрачны (fade), и без подложки проглядывало windowBackground.
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalBottomDockHeight provides dockHeight) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize(),
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
                    LibraryScreen(
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onOpenSearch = {
                            navController.navigate(Screen.Search.route) {
                                // Вкладки сохраняем: поиск открывается поверх стартового графа
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Screen.Wave.route) {
                    WaveScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // Док — оверлей поверх NavHost. Его появление/скрытие анимируется отдельно
        // (slide + fade) и НЕ влияет на размер NavHost — в этом суть фикса «25-го кадра».
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(
                animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
            ) { height -> height } + fadeIn(animationSpec = tween(PlayerSlideMillis)),
            exit = slideOutVertically(
                animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
            ) { height -> height } + fadeOut(animationSpec = tween(PlayerSlideMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ExpressiveBottomDock(
                items = bottomNavItems,
                selectedRoute = selectedRoute,
                onSelect = onTabSelect,
                onExpandClick = onDockExpand,
                viewModel = playerViewModel,
                // Меряем реальную высоту дока для нижнего отступа вкладок.
                modifier = Modifier.onSizeChanged { size -> dockHeightPx = size.height },
            )
        }

        // Плеер — верхний оверлей. NavHost с вкладкой остаётся под ним живым,
        // поэтому при свайпе вниз сквозь тающий плеер видна именно вкладка.
        AnimatedVisibility(
            visible = isPlayerOpen,
            enter = slideInVertically(
                animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
            ) { height -> height },
            exit = slideOutVertically(
                animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
            ) { height -> height },
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerScreen(
                onBack = { isPlayerOpen = false },
                onFindOnYouTube = { track ->
                    playerViewModel.findOnYouTube(track)
                    isPlayerOpen = false
                    navController.navigate(Screen.Search.route) {
                        // Уходим из плеера в поиск, сохраняя состояние вкладок.
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

    // Системный «назад» закрывает плеер, не трогая back stack вкладок.
    BackHandler(enabled = isPlayerOpen) { isPlayerOpen = false }
}

private const val TabTransitionMillis = 300
private const val PlayerSlideMillis = 300

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
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    val direction = tabDirection()
    if (direction == 0) {
        return fadeIn(animationSpec = tween(150))
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
        return fadeOut(animationSpec = tween(150))
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
