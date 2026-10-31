package com.facet.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.facet.music.core.ui.components.ExpressiveBottomDock
import com.facet.music.core.ui.components.FloatingMiniPlayer
import com.facet.music.core.ui.components.MiniPlayerGap
import com.facet.music.core.ui.components.MiniPlayerHorizontalMargin
import com.facet.music.core.ui.components.MiniPlayerIslandHeight
import com.facet.music.core.ui.theme.prewarmPlayerPalette
import com.facet.music.data.model.Track
import com.facet.music.feature.artist.ArtistScreen
import com.facet.music.feature.home.HomeScreen
import com.facet.music.feature.library.LibraryScreen
import com.facet.music.feature.library.LikedTracksScreen
import com.facet.music.feature.player.PlayerSwipeState
import com.facet.music.feature.player.PlayerViewModel
import com.facet.music.feature.player.PlayerScreen
import com.facet.music.feature.playlist.PlaylistScreen
import com.facet.music.feature.search.SearchScreen
import com.facet.music.feature.settings.SettingsScreen
import com.facet.music.feature.wave.WaveScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Плеер — оверлей поверх NavHost, а не отдельный маршрут. Так вкладка
    // остаётся скомпонованной под ним и реально проглядывает при свайпе вниз.
    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }

    // Скрываем BottomBar на полноэкранных экранах (плеер, настройки, плейлист, артист).
    val showBottomBar by remember {
        derivedStateOf {
            val route = navBackStackEntry?.destination?.route
            !isPlayerOpen &&
                route != Screen.Settings.route &&
                route != Screen.LikedTracks.route &&
                route != Screen.Playlist.route &&
                route != Screen.Artist.route
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
    val playerState by playerViewModel.playerState.collectAsState()
    val currentTrack = playerState.currentTrack

    // Последний трек переживает очистку currentTrack: мини-плеер успевает
    // проиграть exit-анимацию и не исчезает рывком.
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(currentTrack) {
        if (currentTrack != null) lastTrack = currentTrack
    }
    val miniTrack = currentTrack ?: lastTrack

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
    val onMiniPlayerExpand: () -> Unit = remember {
        { isPlayerOpen = true }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Состояние свайпа живёт здесь, а не в PlayerScreen: смещение читается
    // внутри graphicsLayer без рекомпозиции на каждый кадр.
    val playerSwipe = remember { PlayerSwipeState(scope) }

    // Видимость плеера держим в MutableTransitionState, чтобы управлять
    // enter/exit-переходом снаружи AnimatedVisibility (в общем оверлее).
    val playerVisibleState = remember { MutableTransitionState(false) }
    SideEffect { playerVisibleState.targetState = isPlayerOpen }

    // Открыть плеер и загрузить трек с очередью
    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        // Прогреваем палитру обложки заранее — к открытию плеера цвет уже в кэше,
        // поэтому фон не пересчитывается и не анимируется прямо во время перехода.
        scope.launch { prewarmPlayerPalette(context, track.artworkUrl) }
        playerViewModel.playTrackWithQueue(track, queue)
        isPlayerOpen = true
    }

    // Измеренная высота навигационного бара (вместе с его navigationBarsPadding).
    // От неё зависят позиция островка мини-плеера и нижний contentPadding вкладок.
    // NavHost при этом остаётся во весь экран и НЕ меняет размер при показе/скрытии
    // бара — отсюда и плавность перехода в плеер.
    val density = LocalDensity.current
    var navBarHeightPx by remember { mutableStateOf(0) }
    val navBarHeight = with(density) { navBarHeightPx.toDp() }

    // Островок показываем только когда есть трек и бар уже измерен: иначе
    // он не знает, на сколько подняться над навигацией.
    val showMiniPlayer = showBottomBar && miniTrack != null && navBarHeightPx > 0

    // Резерв под островок в нижнем contentPadding вкладок. Анимируем, чтобы
    // контент не прыгал в момент появления/исчезновения мини-плеера.
    val miniPlayerReserve by animateDpAsState(
        targetValue = if (showMiniPlayer) MiniPlayerIslandHeight + MiniPlayerGap else 0.dp,
        animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
        label = "miniPlayerReserve",
    )
    val bottomDockHeight = navBarHeight + miniPlayerReserve

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Непрозрачный фон под NavHost: во время перехода вкладок оба экрана
            // полупрозрачны (fade), и без подложки проглядывало windowBackground.
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalBottomDockHeight provides bottomDockHeight) {
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
                        onWaveClick = { navController.navigate(Screen.Wave.route) },
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onImportClick = { navController.navigate(Screen.Import.route) },
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
                        onPlaylistClick = { playlistId ->
                            navController.navigate(Screen.Playlist.createRoute(playlistId))
                        },
                        onLikedTracksClick = {
                            navController.navigate(Screen.LikedTracks.route)
                        },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                // Любимые треки — отдельный экран коллекции: в медиатеке от них
                // остался только крупный чипс.
                composable(Screen.Import.route) {
                    com.facet.music.feature.imports.ImportScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screen.LikedTracks.route) {
                    LikedTracksScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onOpenSearch = {
                            navController.navigate(Screen.Search.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                // Экран плейлиста — отдельный маршрут с id. Нижний док скрыт:
                // это детальный экран, как настройки; back возвращает в медиатеку.
                composable(
                    route = Screen.Playlist.route,
                    arguments = listOf(
                        navArgument(Screen.Playlist.ARG_ID) { type = NavType.LongType },
                    ),
                ) {
                    PlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                // Экран артиста: имя в пути, нижний док скрыт как у деталей.
                composable(
                    route = Screen.Artist.route,
                    arguments = listOf(
                        navArgument(Screen.Artist.ARG_NAME) { type = NavType.StringType },
                    ),
                ) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist ->
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
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

        // Навигационный бар — полноценная панель во всю ширину у нижней кромки.
        // Живёт отдельно от мини-плеера; появление/скрытие анимируется здесь
        // и НЕ влияет на размер NavHost — в этом суть фикса «25-го кадра».
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
                // Меряем реальную высоту бара: от неё зависят позиция островка
                // и нижний отступ контента вкладок.
                modifier = Modifier.onSizeChanged { size -> navBarHeightPx = size.height },
            )
        }

        // Общий SharedTransitionLayout для морфа обложки между мини-плеером
        // и полноэкранным плеером. Слой прозрачный: NavHost и бар остаются
        // под ним, а обложка летит в общем оверлее.
        SharedTransitionLayout(
            modifier = Modifier.fillMaxSize(),
        ) {
            val sharedScope = this
            Box(modifier = Modifier.fillMaxSize()) {
                // Мини-плеер: мягкое проявление без слайда и сильного масштаба —
                // иначе вместе с морфом обложки получается двойная трансформация.
                AnimatedVisibility(
                    visible = showMiniPlayer,
                    enter = fadeIn(animationSpec = tween(PlayerSlideMillis)) + scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
                    ),
                    exit = fadeOut(animationSpec = tween(PlayerSlideMillis)) + scaleOut(
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = tween(PlayerSlideMillis, easing = FastOutSlowInEasing),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = MiniPlayerHorizontalMargin,
                            end = MiniPlayerHorizontalMargin,
                            // 12dp воздуха между островком и верхней кромкой бара.
                            bottom = navBarHeight + MiniPlayerGap,
                        ),
                ) {
                    val animatedScope = this
                    val coverModifier = with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(PlayerCoverSharedKey),
                            animatedVisibilityScope = animatedScope,
                        )
                    }
                    miniTrack?.let { track ->
                        FloatingMiniPlayer(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            onExpandClick = onMiniPlayerExpand,
                            onPlayPauseClick = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.playNext() },
                            onPrevious = { playerViewModel.playPrevious() },
                            currentFraction = { playerViewModel.currentFraction() },
                            coverModifier = coverModifier,
                        )
                    }
                }

                // Плеер — верхний оверлей. NavHost с вкладкой остаётся под ним живым,
                // поэтому при свайпе вниз сквозь тающий плеер видна именно вкладка.
                //
                // Экран только проявляется/тает: единственная трансформация при
                // открытии — морф обложки. Раньше scaleIn/scaleOut накладывались
                // на полёт обложки, и картинка «ехала» дважды — отсюда кривой морф.
                val playerEnter = fadeIn(
                    animationSpec = tween(durationMillis = 180),
                )
                val playerExit = fadeOut(
                    animationSpec = tween(durationMillis = 160),
                )
                AnimatedVisibility(
                    visibleState = playerVisibleState,
                    // Вход без полноэкранного слайда: экран мягко проявляется,
                    // а обложка через sharedElement летит из мини-плеера.
                    enter = playerEnter,
                    // Фон экрана мягко сжимается и тает, а обложка через
                    // sharedElement перелетает в мини-плеер.
                    exit = playerExit,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val animatedScope = this
                    val coverModifier = with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(PlayerCoverSharedKey),
                            animatedVisibilityScope = animatedScope,
                        )
                    }
                    // Панели с текстом и кнопками рисуются в оверлее shared-перехода
                    // с zIndex выше обложки: морф проходит под ними, а не поверх.
                    // Вне enter/exit модификатор не активен, чтобы панели не
                    // перекрывали снекбар и мини-плеер.
                    val foregroundModifier = with(sharedScope) {
                        Modifier
                            .renderInSharedTransitionScopeOverlay(
                                renderInOverlay = {
                                    !isPlayerOpen || animatedScope.transition.isRunning
                                },
                                zIndexInOverlay = 1f,
                            )
                            .then(
                                with(animatedScope) {
                                    Modifier.animateEnterExit(enter = playerEnter, exit = playerExit)
                                },
                            )
                    }
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
                        onArtistClick = { artist ->
                            // Плеер — оверлей: закрываем его и открываем экран артиста.
                            // Воспроизведение не прерывается, PlayerController общий.
                            isPlayerOpen = false
                            navController.navigate(Screen.Artist.createRoute(artist))
                        },
                        viewModel = playerViewModel,
                        coverModifier = coverModifier,
                        foregroundModifier = foregroundModifier,
                        isClosing = !isPlayerOpen,
                        swipeState = playerSwipe,
                    )
                }
            }
        }
    }

    // Системный «назад» закрывает плеер, не трогая back stack вкладок.
    BackHandler(enabled = isPlayerOpen) { isPlayerOpen = false }
}

private const val TabTransitionMillis = 300
private const val PlayerSlideMillis = 300

/** Общий ключ обложки для морфа между полноэкранным и мини-плеером. */
private const val PlayerCoverSharedKey = "player-cover"

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
