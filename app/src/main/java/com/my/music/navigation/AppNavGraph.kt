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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Открыть плеер и загрузить трек с очередью
    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        // Прогреваем палитру обложки заранее — к открытию плеера цвет уже в кэше,
        // поэтому фон не пересчитывается и не анимируется прямо во время перехода.
        scope.launch { prewarmPlayerPalette(context, track.artworkUrl) }
        playerViewModel.playTrackWithQueue(track, queue)
        navController.navigate(Screen.Player.route) {
            // Защита от дублей плеера в стеке: иначе повторный pop
            // проигрывал бы slide-вход плеера поверх самого себя.
            launchSingleTop = true
        }
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
                composable(
                    route = Screen.Player.route,
                    // Плеер наезжает вертикальным слайдом, как «шторка». Slide — это
                    // дешёвая трансляция без offscreen alpha-слоя, поэтому тяжёлый
                    // экран не роняет кадры (fade оборачивал весь сабтри в alpha-слой).
                    // Нижний экран держится на месте — см. tabEnter/tabExitTransition.
                    enterTransition = {
                        slideInVertically(
                            animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
                        ) { height -> height }
                    },
                    // Уход из плеера не кнопкой «назад» (например, в поиск) — быстрый fade.
                    exitTransition = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                    popExitTransition = {
                        slideOutVertically(
                            animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
                        ) { height -> height }
                    },
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
    }
}

private const val TabTransitionMillis = 300
private const val PlayerSlideMillis = 300

/**
 * true, если маршрут — полноэкранный плеер.
 */
private fun NavBackStackEntry.isPlayerRoute(): Boolean =
    destination.route == Screen.Player.route

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
 * С полноэкранными маршрутами — быстрый кросс-фейд вместо слайда:
 * тяжёлый плеер не должен проигрывать slide-анимации с просадками кадров.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    // Возврат из плеера (плеер уезжает вниз) — нижний экран уже на месте.
    // Держим его видимым и неподвижным (alpha=1), не проигрывая «въезд».
    if (initialState.isPlayerRoute()) {
        return fadeIn(animationSpec = tween(PlayerSlideMillis), initialAlpha = 1f)
    }
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
 * Для полноэкранных маршрутов — быстрый fade, симметричный входу.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(): ExitTransition {
    // Открытие плеера (плеер наезжает сверху) — нижний экран остаётся на месте и
    // полностью видимым (alpha=1), пока плеер его не закроет. ExitTransition.None
    // здесь не годится: экран убрался бы мгновенно и за наезжающим плеером мелькал
    // бы пустой фон.
    if (targetState.isPlayerRoute()) {
        return fadeOut(animationSpec = tween(PlayerSlideMillis), targetAlpha = 1f)
    }
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
