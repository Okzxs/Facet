package com.facet.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.prewarmPlayerPalette
import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.feature.artist.ArtistScreen
import com.facet.music.feature.home.HomeScreen
import com.facet.music.feature.library.LibraryScreen
import com.facet.music.feature.library.LikedTracksScreen
import com.facet.music.feature.player.DISMISS_THRESHOLD
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

    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isPlayerOpen) {
        if (isPlayerOpen) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val showBottomBar by remember {
        derivedStateOf {
            val route = navBackStackEntry?.destination?.route
            !isPlayerOpen &&
                route != Screen.Settings.route &&
                route != Screen.LikedTracks.route &&
                route != Screen.Playlist.route &&
                route != Screen.Artist.route &&
                route != Screen.ArtistDetail.route
        }
    }

    val isLikedTracksScreen =
        navBackStackEntry?.destination?.route == Screen.LikedTracks.route

    val selectedRoute by remember {
        derivedStateOf {
            val route = when (val current = navBackStackEntry?.destination?.route) {
                Screen.Import.route -> Screen.Library.route
                else -> current
            }
            bottomNavItems.firstOrNull { item -> item.screen.route == route }
                ?.screen?.route
        }
    }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.playerState.collectAsState()
    val currentTrack = playerState.currentTrack

    var lastTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(currentTrack) {
        if (currentTrack != null) lastTrack = currentTrack
    }
    val miniTrack = currentTrack ?: lastTrack

    var isMiniPlayerDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(currentTrack) {
        if (currentTrack != null) isMiniPlayerDismissed = false
    }

    val onTabSelect: (BottomNavItem) -> Unit = remember(navController) {
        { item ->
            val current = navController.currentDestination?.route
            val popped = current != item.screen.route &&
                navController.popBackStack(item.screen.route, inclusive = false)
            if (!popped && current != item.screen.route) {
                navController.navigate(item.screen.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    val onMiniPlayerExpand: () -> Unit = remember {
        { isPlayerOpen = true }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playerSwipe = remember { PlayerSwipeState(scope) }

    val playerVisibleState = remember { MutableTransitionState(false) }
    SideEffect { playerVisibleState.targetState = isPlayerOpen }

    fun openArtist(artist: Artist) {
        navController.navigate(
            Screen.ArtistDetail.createRoute(artist.source, artist.url, artist.name),
        )
    }

    fun openArtist(track: Track) {
        val url = track.artistUrl
        if (url != null && (track.source == Source.YOUTUBE || track.source == Source.SOUNDCLOUD)) {
            navController.navigate(
                Screen.ArtistDetail.createRoute(track.source, url, track.artist),
            )
        } else {
            navController.navigate(Screen.Artist.createRoute(track.artist))
        }
    }

    fun openPlayer(track: Track, queue: List<Track> = listOf(track)) {
        scope.launch { prewarmPlayerPalette(context, track.artworkUrl) }
        playerViewModel.playTrackWithQueue(track, queue)
        isPlayerOpen = true
    }

    val density = LocalDensity.current
    var navBarHeightPx by remember { mutableStateOf(0) }
    val navBarHeight = with(density) { navBarHeightPx.toDp() }
    val navigationBarInset = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }

    val showMiniPlayer = (showBottomBar || (isLikedTracksScreen && !isPlayerOpen)) &&
        miniTrack != null &&
        (navBarHeightPx > 0 || isLikedTracksScreen) &&
        !isMiniPlayerDismissed

    val miniPlayerReserve by animateDpAsState(
        targetValue = if (showMiniPlayer) MiniPlayerIslandHeight + MiniPlayerGap else 0.dp,
        animationSpec = Motion.spatialDefault(),
        label = "miniPlayerReserve",
    )
    val bottomDockHeight = navBarHeight + miniPlayerReserve

    val miniPlayerBottom by animateDpAsState(
        targetValue = if (showBottomBar) {
            navBarHeight + MiniPlayerGap
        } else {
            navigationBarInset + MiniPlayerGap
        },
        animationSpec = Motion.spatialDefault(),
        label = "miniPlayerBottom",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        onWaveClick = {
                            navController.navigate(Screen.Wave.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist -> openArtist(artist) },
                        onTrackArtistClick = { track -> openArtist(track) },
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
                        onArtistClick = { track -> openArtist(track) },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
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
                        onArtistClick = { track -> openArtist(track) },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                        bottomInset = miniPlayerReserve,
                    )
                }
                composable(
                    route = Screen.Playlist.route,
                    arguments = listOf(
                        navArgument(Screen.Playlist.ARG_ID) { type = NavType.LongType },
                    ),
                ) {
                    PlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { track -> openArtist(track) },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                composable(
                    route = Screen.Artist.route,
                    arguments = listOf(
                        navArgument(Screen.Artist.ARG_NAME) { type = NavType.StringType },
                    ),
                ) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist -> openArtist(artist) },
                        onTrackArtistClick = { track -> openArtist(track) },
                        onWaveClick = { artist ->
                            playerViewModel.startWaveFromArtist(artist)
                            isPlayerOpen = true
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(
                        navArgument(Screen.ArtistDetail.ARG_SOURCE) { type = NavType.StringType },
                        navArgument(Screen.ArtistDetail.ARG_URL) { type = NavType.StringType },
                        navArgument(Screen.ArtistDetail.ARG_NAME) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, queue -> openPlayer(track, queue) },
                        onArtistClick = { artist -> openArtist(artist) },
                        onTrackArtistClick = { track -> openArtist(track) },
                        onWaveClick = { artist ->
                            playerViewModel.startWaveFromArtist(artist)
                            isPlayerOpen = true
                        },
                        onPlayNext = { track -> playerViewModel.playNext(track) },
                        onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    )
                }
                composable(Screen.Wave.route) {
                    WaveScreen(onArtistClick = { track -> openArtist(track) })
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(
                animationSpec = Motion.spatialDefault(),
            ) { height -> height } + fadeIn(animationSpec = Motion.effectsDefault()),
            exit = slideOutVertically(
                animationSpec = Motion.spatialDefault(),
            ) { height -> height } + fadeOut(animationSpec = Motion.effectsDefault()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ExpressiveBottomDock(
                items = bottomNavItems,
                selectedRoute = selectedRoute,
                onSelect = onTabSelect,
                modifier = Modifier.onSizeChanged { size -> navBarHeightPx = size.height },
            )
        }

        SharedTransitionLayout(
            modifier = Modifier.fillMaxSize(),
        ) {
            val sharedScope = this
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showMiniPlayer,
                    enter = fadeIn(animationSpec = Motion.effectsDefault()) + scaleIn(
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = Motion.spatialDefault(),
                    ),
                    exit = fadeOut(animationSpec = Motion.effectsDefault()) + scaleOut(
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = Motion.spatialDefault(),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = MiniPlayerHorizontalMargin,
                            end = MiniPlayerHorizontalMargin,
                            bottom = miniPlayerBottom,
                        ),
                ) {
                    val animatedScope = this
                    val coverModifier = with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(PlayerCoverSharedKey),
                            animatedVisibilityScope = animatedScope,
                            boundsTransform = { _, _ -> Motion.spatialDefault<Rect>() },
                        )
                    }
                    miniTrack?.let { track ->
                        FloatingMiniPlayer(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isBuffering || playerState.isLoading,
                            onExpandClick = onMiniPlayerExpand,
                            onPlayPauseClick = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.playNext() },
                            onPrevious = { playerViewModel.playPrevious(force = true) },
                            onCloseClick = {
                                isMiniPlayerDismissed = true
                                playerViewModel.clearQueue()
                            },
                            currentFraction = { playerViewModel.currentFraction() },
                            coverModifier = coverModifier,
                        )
                    }
                }

                val playerEnter = fadeIn(
                    animationSpec = tween(
                        durationMillis = Motion.EnterMillis,
                        easing = Motion.EmphasizedDecelerate,
                    ),
                )
                val playerExit = fadeOut(
                    animationSpec = tween(
                        durationMillis = Motion.ExitMillis,
                        easing = Motion.EmphasizedAccelerate,
                    ),
                )
                AnimatedVisibility(
                    visibleState = playerVisibleState,
                    enter = playerEnter,
                    exit = playerExit,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val animatedScope = this
                    val coverModifier = with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(PlayerCoverSharedKey),
                            animatedVisibilityScope = animatedScope,
                            boundsTransform = { _, _ -> Motion.spatialDefault<Rect>() },
                        )
                    }
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
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onArtistClick = { track ->
                            isPlayerOpen = false
                            openArtist(track)
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

    PredictiveBackHandler(enabled = isPlayerOpen) { progressFlow ->
        playerSwipe.beginDrag()
        try {
            progressFlow.collect { event ->
                val height = playerSwipe.screenHeightPx
                if (height > 0f) {
                    playerSwipe.dragTo(event.progress * height * 0.9f)
                }
            }
        } finally {
            if (!playerSwipe.isDismissed) {
                val height = playerSwipe.screenHeightPx
                val passed = playerSwipe.offsetPx > height * DISMISS_THRESHOLD
                if (passed) {
                    playerSwipe.dismiss()
                    isPlayerOpen = false
                } else {
                    playerSwipe.settle()
                }
            }
        }
    }
}

private const val PlayerCoverSharedKey = "player-cover"

private fun NavBackStackEntry.tabIndex(): Int =
    bottomNavItems.indexOfFirst { item ->
        destination.hierarchy.any { it.route == item.screen.route }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = initialState.tabIndex()
    val to = targetState.tabIndex()
    if (from < 0 || to < 0 || from == to) return 0
    return if (to > from) 1 else -1
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    val direction = tabDirection()
    if (direction == 0) {
        return fadeIn(animationSpec = Motion.effectsFast())
    }
    return slideInHorizontally(
        initialOffsetX = { width -> direction * width / 3 },
        animationSpec = Motion.spatialDefault(),
    ) + fadeIn(
        animationSpec = Motion.effectsDefault(),
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(): ExitTransition {
    val direction = tabDirection()
    if (direction == 0) {
        return fadeOut(animationSpec = Motion.effectsFast())
    }
    return slideOutHorizontally(
        targetOffsetX = { width -> -direction * width / 3 },
        animationSpec = Motion.spatialDefault(),
    ) + fadeOut(
        animationSpec = Motion.effectsDefault(),
    )
}
