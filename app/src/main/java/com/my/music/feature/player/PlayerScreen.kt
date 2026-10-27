package com.my.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.download.DownloadMessages
import com.my.music.core.download.DownloadState
import com.my.music.core.location.LocationPermissions
import com.my.music.core.ui.components.ExpressivePlayerSlider
import com.my.music.core.ui.components.PlayerControlIsland
import com.my.music.core.ui.components.PlayerQueueSheet
import com.my.music.core.ui.components.PlayerSliderState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.my.music.core.ui.theme.PlayerDynamicColors
import com.my.music.core.ui.theme.rememberPlayerDynamicColors
import com.my.music.data.model.LyricsResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Вариант компоновки hero-зоны (для сравнения на устройстве, поменяй цифру и пересобери):
 * 1 — «Редакция»: наклонённая обложка по центру, стеклянная карточка с огромным названием свисает с её нижнего края;
 * 2 — «Смещение»: обложка свисает за правый край экрана, карточка с названием — слева;
 * 3 — «На обложке»: название лежит прямо на обложке в матово-стеклянной плашке.
 */
private const val PLAYER_LAYOUT_VARIANT = 1

/** Ошибка скачивания без альтернативы (например, YouTube). */
private const val DOWNLOAD_FAILED_MESSAGE = "Не удалось скачать трек (доступен только онлайн)"

private const val FIND_ON_YOUTUBE_ACTION = "Найти"

/** Параметры компоновки для конкретного варианта. */
private data class HeroStyle(
    val widthFraction: Float,
    val rotationZ: Float,
    val alignment: Alignment,
    val topPadding: Dp,
    val offsetX: Dp,
    val overlap: Dp,
    val titleOnArtwork: Boolean,
)

private fun heroStyle(variant: Int): HeroStyle = when (variant) {
    2 -> HeroStyle(0.62f, 7f, Alignment.TopEnd, 26.dp, 16.dp, 60.dp, false)
    3 -> HeroStyle(0.94f, -3f, Alignment.TopCenter, 4.dp, 0.dp, 0.dp, true)
    else -> HeroStyle(0.88f, -5f, Alignment.TopCenter, 6.dp, 0.dp, 46.dp, false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onFindOnYouTube: (Track) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    var isLyricsMode by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ─── Разрешение на геопозицию ────────────────────────────────────────
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(LocationPermissions.hasPermission(context))
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var pendingLike by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        hasLocationPermission = result.values.any { it } ||
            LocationPermissions.hasPermission(context)
        // Лайк не теряем: без разрешения он сохранится без координат
        if (pendingLike) {
            pendingLike = false
            viewModel.toggleLike()
        }
    }

    // Разрешение могли выдать или отозвать в системных настройках — обновляем статус
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = LocationPermissions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleLikeWithPermission() {
        if (!isLiked && !LocationPermissions.hasPermission(context)) {
            pendingLike = true
            locationPermissionLauncher.launch(LocationPermissions.permissions)
        } else {
            viewModel.toggleLike()
        }
    }

    fun onPermissionBannerAction() {
        if (permissionRequested && !LocationPermissions.hasPermission(context)) {
            LocationPermissions.openAppSettings(context)
        } else {
            locationPermissionLauncher.launch(LocationPermissions.permissions)
        }
    }

    // Текущий трек берём из PlayerController (он всегда актуален после play())
    val track = playerState.currentTrack ?: uiState.track

    // Скачивание невозможно (нет прав/404/HLS-ошибка) — сообщаем пользователю.
    // Крестик живёт секунду, поэтому Snackbar запускаем в scope композиции,
    // иначе смена состояния отменила бы его вместе с LaunchedEffect.
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(downloadState, track?.id) {
        val error = downloadState as? DownloadState.Error
        if (error?.cause != DownloadState.Cause.FAILED || track == null) return@LaunchedEffect

        val isSoundCloud = track.source == Source.SOUNDCLOUD
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = if (isSoundCloud) {
                    DownloadMessages.SOURCE_NOT_FOUND
                } else {
                    DOWNLOAD_FAILED_MESSAGE
                },
                actionLabel = if (isSoundCloud) FIND_ON_YOUTUBE_ACTION else null,
                duration = if (isSoundCloud) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (isSoundCloud && result == SnackbarResult.ActionPerformed) {
                onFindOnYouTube(track)
            }
        }
    }

    // Резолвер не нашёл progressive-источник: воркер даже не создавался.
    LaunchedEffect(uiState.downloadError) {
        val message = uiState.downloadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeDownloadError()
    }

    // Гарантируем запрос текста при открытии экрана или смене трека
    LaunchedEffect(track?.id) {
        if (track != null && uiState.lyrics == null && !uiState.isLyricsLoading) {
            viewModel.loadLyricsIfNeeded(track)
        }
    }

    // ─── Тонированный фон и палитра по обложке (Palette) ────────────────
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultOnPrimary = MaterialTheme.colorScheme.onPrimary
    val defaultPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val defaultOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    // Динамические цвета включены — палитра от обложки; выключены — акцент из темы
    val playerColors = rememberPlayerDynamicColors(
        artworkUrl = if (appSettings.dynamicCoverColors) track?.artworkUrl else null,
        defaultSurface = defaultSurface,
        defaultPrimary = defaultPrimary,
        defaultOnPrimary = defaultOnPrimary,
        defaultPrimaryContainer = defaultPrimaryContainer,
        defaultOnPrimaryContainer = defaultOnPrimaryContainer,
    )

    val animatedBgTop by animateColorAsState(
        targetValue = playerColors.backgroundTop,
        animationSpec = tween(durationMillis = 400),
        label = "bgTopAnim",
    )
    val animatedBgBottom by animateColorAsState(
        targetValue = playerColors.backgroundBottom,
        animationSpec = tween(durationMillis = 400),
        label = "bgBottomAnim",
    )

    val backgroundBrush = remember(animatedBgTop, animatedBgBottom, defaultSurface) {
        // Тонировка композитится поверх непрозрачного surface: полупрозрачный
        // верх градиента иначе просвечивал экран под плеером при переходах.
        Brush.verticalGradient(
            colors = listOf(
                animatedBgTop.compositeOver(defaultSurface),
                animatedBgBottom.compositeOver(defaultSurface),
                defaultSurface,
            ),
        )
    }

    // Фон режима текста: градиент акцента палитры, проявляющийся на весь экран.
    // Отдельная анимация нужна, потому что слой живёт в корне экрана, а не в hero-зоне.
    val lyricsBackdrop by animateFloatAsState(
        targetValue = if (isLyricsMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricsBackdrop",
    )

    val surfaceColor = playerColors.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    Scaffold(
        modifier = Modifier
            // Непрозрачный градиент на весь экран, включая полосу под прозрачной
            // TopAppBar: иначе главная просвечивала сквозь плеер при переходах.
            .background(backgroundBrush),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Назад")
                    }
                },
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "СЕЙЧАС ИГРАЕТ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = track?.artist ?: "—",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                // Мягкое свечение именем акцента палитры
                                shadow = Shadow(
                                    color = playerColors.primary.copy(alpha = 0.55f),
                                    blurRadius = 8f,
                                ),
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ─── Фон режима текста: градиент от акцента палитры к surface ───
            // Blur обложки убран полностью: Modifier.blur давал просадки FPS
            // при анимациях. Градиент имитирует тонировку фона без размытия.
            if (lyricsBackdrop > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = lyricsBackdrop }
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    playerColors.primary.copy(alpha = 0.45f),
                                    playerColors.primaryContainer.copy(alpha = 0.22f),
                                    defaultSurface,
                                ),
                            ),
                        ),
                )
            }

            // Спокойный scrim поверх фона: держит контраст текста на любой обложке
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 0.65f * lyricsBackdrop }
                    .background(MaterialTheme.colorScheme.surface),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ─── Hero-зона: обложка-задник + передний план (обложка/тексты) ──
                ExpressiveHeroZone(
                    variant = PLAYER_LAYOUT_VARIANT,
                    title = track?.title ?: "Нет трека",
                    artist = track?.artist ?: "—",
                    artworkUrl = track?.artworkUrl,
                    isLiked = isLiked,
                    downloadState = downloadState,
                    isLyricsMode = isLyricsMode,
                    lyricsResult = uiState.lyrics,
                    isLyricsLoading = uiState.isLyricsLoading,
                    lyricsError = uiState.lyricsError,
                    palette = playerColors,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                    currentPositionMs = { viewModel.currentPositionMs() },
                    onSeekToLine = { timeMs -> viewModel.seekToMs(timeMs) },
                    onRetryLyrics = { viewModel.loadLyricsIfNeeded(track) },
                    onToggleLike = { toggleLikeWithPermission() },
                    onDownloadClick = { viewModel.toggleDownload() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                // ─── Подсказка про геопозицию для «Воспоминаний» ─────────────
                if (!hasLocationPermission) {
                    LocationPermissionBanner(
                        actionLabel = if (permissionRequested) "Настройки" else "Разрешить",
                        onAction = { onPermissionBannerAction() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp),
                    )
                }

                // ─── Сообщение об ошибке (скрывается, как только плеер в STATE_READY) ──
                val isPlayerReady = playerState.playbackState == androidx.media3.common.Player.STATE_READY
                val errorMessage = if (isPlayerReady) null else (uiState.error ?: playerState.error)

                if (errorMessage != null) {
                    Text(
                        text = "⚠️ $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }

                // ─── Плавающий остров управления ─────────────────────────
                PlayerControlIsland(
                    isPlaying = playerState.isPlaying,
                    isBuffering = isBuffering,
                    playbackSpeed = playerState.playbackSpeed,
                    isLyricsActive = isLyricsMode,
                    containerColor = surfaceColor.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    accentColor = playerColors.primary,
                    onAccentColor = playerColors.onPrimary,
                    lyricsActiveContainer = playerColors.primaryContainer,
                    lyricsActiveContent = playerColors.onPrimaryContainer,
                    borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    onPlayPause = { viewModel.togglePlayPause() },
                    onPrev = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onSpeedClick = { viewModel.cyclePlaybackSpeed() },
                    onQueueClick = { showQueueSheet = true },
                    onLyricsClick = {
                        val nextMode = !isLyricsMode
                        isLyricsMode = nextMode
                        if (nextMode) {
                            viewModel.loadLyricsIfNeeded(track)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        // Запас под выступающий язычок «Текст» и воздух до текстов/обложки
                        .padding(top = 30.dp),
                ) {
                    PlayerProgressSection(
                        isPlaying = playerState.isPlaying,
                        isBuffering = isBuffering,
                        isResolving = uiState.isResolving,
                        trackId = track?.id,
                        durationMs = track?.durationMs ?: 0L,
                        primaryColor = playerColors.primary,
                        currentFraction = { viewModel.currentFraction() },
                        currentPositionMs = { viewModel.currentPositionMs() },
                        bufferedFraction = { viewModel.bufferedFraction() },
                        onSeek = { fraction -> viewModel.seekTo(fraction) },
                    )
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }

    // Шторка закрывается только мгновенным удалением из композиции (snap),
    // без hide-анимации: анимированное скрытие во время выхода из плеера
    // оставляло бы промежуточные кадры «полуплеера» без контролов.
    if (showQueueSheet) {
        PlayerQueueSheet(
            queue = playerState.queue,
            currentIndex = playerState.currentIndex,
            isShuffle = playerState.isShuffle,
            repeatMode = playerState.repeatMode,
            onTrackClick = { index -> viewModel.playQueueTrack(index) },
            onRemoveTrack = { index -> viewModel.removeQueueTrack(index) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onDismiss = { showQueueSheet = false },
        )
    }
}

/**
 * Ненавязчивая подсказка: без геопозиции лайк сохранится без места,
 * а «Воспоминания» не смогут привязать трек к точке на карте.
 */
@Composable
private fun LocationPermissionBanner(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Геопозиция выключена",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "«Воспоминания» не сохранят место лайка",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Hero-зона плеера:
 * - постоянный слой-задник: наклонённая обложка со свечением, параллаксом и «уходом на задний план»;
 * - передний план через AnimatedContent: карточка трека ИЛИ стеклянный экран текстов.
 */
@Composable
private fun ExpressiveHeroZone(
    variant: Int,
    title: String,
    artist: String,
    artworkUrl: String?,
    isLiked: Boolean,
    downloadState: DownloadState,
    isLyricsMode: Boolean,
    lyricsResult: LyricsResult?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    palette: PlayerDynamicColors,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = heroStyle(variant)
    val heroScroll = rememberScrollState()

    // «Уход на задний план»: при входе в тексты обложка уменьшается, мутнеет и тонет в тени
    val recede by animateFloatAsState(
        targetValue = if (isLyricsMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "artworkRecede",
    )

    val artworkShape = RoundedCornerShape(
        topStart = 56.dp,
        topEnd = 24.dp,
        bottomEnd = 56.dp,
        bottomStart = 24.dp,
    )

    Box(modifier = modifier) {
        // ─── Постоянный слой-задник: обложка ───
        Box(
            modifier = Modifier
                .align(style.alignment)
                .padding(top = style.topPadding)
                .fillMaxWidth(style.widthFraction)
                .aspectRatio(1f)
                .offset(x = style.offsetX, y = (26f * recede).dp)
                .graphicsLayer {
                    rotationZ = lerp(style.rotationZ, style.rotationZ * 2.4f, recede)
                    scaleX = lerp(1f, 0.58f, recede)
                    scaleY = lerp(1f, 0.58f, recede)
                    // В режиме текста карточка полностью уступает место градиентному
                    // фону — иначе её прямоугольные края видны как «кирпич».
                    alpha = lerp(1f, 0f, recede)
                    translationY = heroScroll.value * 0.32f
                },
        ) {
            // Свечение: размытое цветное облако палитры под обложкой.
            // В режиме текста оно гаснет вместе с обложкой, чтобы фон не «радужил».
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.22f
                        scaleY = 1.22f
                        alpha = 1f - recede
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.45f),
                                palette.primaryContainer.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 22.dp,
                        shape = artworkShape,
                        clip = false,
                        ambientColor = palette.primary.copy(alpha = 0.60f),
                        spotColor = palette.primary,
                    )
                    .clip(artworkShape),
                shape = artworkShape,
                color = palette.surfaceContainerHigh,
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = artworkShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }

            // Вариант 3: название прямо на обложке — в матово-стеклянной плашке
            if (style.titleOnArtwork) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = palette.surfaceContainerHigh.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // ─── Передний план ───
        AnimatedContent(
            targetState = isLyricsMode,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState) {
                    // Обложка → Текст: тексты въезжают снизу, обложка уходит вверх
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    ) { it / 3 } + fadeIn(tween(durationMillis = 240))) togetherWith
                        (fadeOut(tween(durationMillis = 180)) + slideOutVertically(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        ) { -it / 5 })
                } else {
                    // Текст → Обложка: зеркальный переход
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    ) { -it / 5 } + fadeIn(tween(durationMillis = 240))) togetherWith
                        (fadeOut(tween(durationMillis = 180)) + slideOutVertically(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        ) { it / 3 })
                }
            },
            label = "playerModeContent",
        ) { lyricsMode ->
            if (lyricsMode) {
                LyricsForeground(
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    lyricsResult = lyricsResult,
                    isLoading = isLyricsLoading,
                    error = lyricsError,
                    highlightColor = palette.primary,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                    currentPositionMs = currentPositionMs,
                    onSeekToLine = onSeekToLine,
                    onRetry = onRetryLyrics,
                )
            } else {
                CoverForeground(
                    style = style,
                    heroScroll = heroScroll,
                    title = title,
                    artist = artist,
                    isLiked = isLiked,
                    downloadState = downloadState,
                    primaryColor = palette.primary,
                    primaryContainerColor = palette.primaryContainer,
                    surfaceColor = palette.surfaceContainerHigh,
                    onToggleLike = onToggleLike,
                    onDownloadClick = onDownloadClick,
                )
            }
        }
    }
}

/**
 * Передний план режима обложки: прозрачный «слот» под обложку (сама обложка — в заднем слое),
 * под ним — стеклянная карточка с Display-названием и артистом.
 */
@Composable
private fun CoverForeground(
    style: HeroStyle,
    heroScroll: ScrollState,
    title: String,
    artist: String,
    isLiked: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(heroScroll)
            .padding(horizontal = 24.dp),
        horizontalAlignment = if (style.alignment == Alignment.TopEnd) Alignment.End else Alignment.Start,
    ) {
        Spacer(Modifier.height(style.topPadding))

        // Слот под обложку: точно совпадает с задним слоем
        Spacer(
            modifier = Modifier
                .fillMaxWidth(style.widthFraction)
                .aspectRatio(1f),
        )

        if (style.titleOnArtwork) {
            // Вариант 3: название уже на обложке — снизу только артист и лайк
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                DownloadSquircle(
                    state = downloadState,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onClick = onDownloadClick,
                )
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            TrackGlassCard(
                title = title,
                artist = artist,
                isLiked = isLiked,
                downloadState = downloadState,
                primaryColor = primaryColor,
                primaryContainerColor = primaryContainerColor,
                surfaceColor = surfaceColor,
                onToggleLike = onToggleLike,
                onDownloadClick = onDownloadClick,
                modifier = Modifier.offset(y = -style.overlap),
            )
        }

        // Гарантированный воздух между карточкой и островом управления
        Spacer(Modifier.height(20.dp))
    }
}

/** Матово-стеклянная карточка трека с огромной Display-типографикой. */
@Composable
private fun TrackGlassCard(
    title: String,
    artist: String,
    isLiked: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomEnd = 34.dp, bottomStart = 12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = cardShape,
                clip = false,
                ambientColor = primaryColor.copy(alpha = 0.35f),
                spotColor = primaryColor.copy(alpha = 0.25f),
            ),
        shape = cardShape,
        color = surfaceColor.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            // Огромная типографика Display (Google Sans Flex)
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Акцентная полоска-градиент под названием
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(primaryColor, primaryColor.copy(alpha = 0f)),
                            ),
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                DownloadSquircle(
                    state = downloadState,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onClick = onDownloadClick,
                )
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
            }
        }
    }
}

/** Визуальные состояния кнопки: прогресс не перезапускает анимацию иконки. */
private enum class DownloadButtonKind {
    Download,
    Progress,
    Done,
    Error,
}

private fun DownloadState.toButtonKind(): DownloadButtonKind = when (this) {
    is DownloadState.Downloaded -> DownloadButtonKind.Done
    is DownloadState.InProgress -> DownloadButtonKind.Progress
    is DownloadState.Error -> DownloadButtonKind.Error
    DownloadState.NotDownloaded -> DownloadButtonKind.Download
}

/**
 * Кнопка скачивания 46dp: стрелка → прогресс → галочка,
 * при отмене/ошибке — крестик, который через секунду морфится обратно.
 */
@Composable
private fun DownloadSquircle(
    state: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    onClick: () -> Unit,
) {
    val kind = state.toButtonKind()
    val progress = (state as? DownloadState.InProgress)?.progress ?: 0f
    val contentColor = when (kind) {
        DownloadButtonKind.Done -> primaryColor
        DownloadButtonKind.Error -> MaterialTheme.colorScheme.error
        DownloadButtonKind.Download, DownloadButtonKind.Progress ->
            MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when (kind) {
        DownloadButtonKind.Done -> primaryContainerColor
        DownloadButtonKind.Error ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        DownloadButtonKind.Download, DownloadButtonKind.Progress ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
            },
            label = "downloadIcon",
        ) { target ->
            when (target) {
                DownloadButtonKind.Download -> Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Скачать трек",
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Progress -> if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                    )
                }

                DownloadButtonKind.Done -> Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = "Трек скачан",
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Error -> Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Скачивание отменено",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Лайк-сквиркл 46dp с тональным контейнером. */
@Composable
private fun LikeSquircle(
    isLiked: Boolean,
    primaryColor: Color,
    primaryContainerColor: Color,
    onToggleLike: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onToggleLike,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(16.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isLiked) primaryContainerColor
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            contentColor = if (isLiked) primaryColor else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Лайк",
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Чистый экран текстов поверх «утопленной» размытой обложки:
 * без стеклянных панелей — только парящая шапка трека и строки, растворяющиеся в фоне.
 */
@Composable
private fun LyricsForeground(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    error: String?,
    highlightColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val miniShape = RoundedCornerShape(13.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Парящая шапка: миниатюра и подпись без подложки
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(miniShape),
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = miniShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.44f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Строки скользят под мягкими градиентными масками, не упираясь в контролы.
        // Маски — это DstIn-затухание самого контента, а не серая плёнка surface:
        // на светлой обложке цветная подложка давала видимый прямоугольник.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LyricsListView(
                lyricsResult = lyricsResult,
                isLoading = isLoading,
                error = error,
                highlightColor = highlightColor,
                currentPositionMs = currentPositionMs,
                onSeekToLine = onSeekToLine,
                onRetry = onRetry,
            )
        }

        // Воздух до острова управления: тексты не налезают на язычок и волну
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ArtworkImage(
    artworkUrl: String?,
    shape: RoundedCornerShape,
    notePainter: Painter,
    placeholderPainter: Painter,
) {
    val context = LocalContext.current
    if (!artworkUrl.isNullOrBlank()) {
        val imageRequest = remember(artworkUrl) {
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .crossfade(200)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Обложка",
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = notePainter,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = notePainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Список строк текста без панелей и подложек:
 * - крупная базовая строка, активная выделяется масштабом, весом и свечением;
 * - дальние строки приглушаются и мягко размываются;
 * - сверху и снизу строки растворяются в градиентных масках;
 * - автоцентрирование активной строки по вертикали (как в Apple Music);
 * - тап = перемотка.
 */
@Composable
private fun LyricsListView(
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    error: String?,
    highlightColor: Color,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    var activeIndex by remember { mutableIntStateOf(-1) }
    var activeProgress by remember { mutableFloatStateOf(0f) }
    var hasCentered by remember { mutableStateOf(false) }

    // Высота видимой области — для центрирующего сдвига автоскролла
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val halfLinePx = with(density) { 22.dp.roundToPx() }
    // Активная строка стоит чуть выше геометрического центра: так перенесённая
    // строка не попадает под нижнюю градиентную маску у контролов.
    val centerLiftPx = with(density) { 24.dp.roundToPx() }

    // Опрос позиции: индекс активной строки и прогресс внутри неё
    LaunchedEffect(lyricsResult) {
        activeIndex = -1
        activeProgress = 0f
        hasCentered = false
        while (true) {
            val lines = lyricsResult?.lines
            if (lyricsResult?.isSynced == true && !lines.isNullOrEmpty()) {
                val curTime = currentPositionMs()
                val idx = lines.indexOfLast { it.timeMs <= curTime }
                if (idx != activeIndex) activeIndex = idx
                activeProgress = if (idx in lines.indices) {
                    val startMs = lines[idx].timeMs.coerceAtLeast(0L)
                    val endMs = if (idx + 1 < lines.size) lines[idx + 1].timeMs else startMs + 5000L
                    ((curTime - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L))
                        .coerceIn(0f, 1f)
                } else {
                    0f
                }
            } else {
                activeProgress = 0f
            }
            delay(150)
        }
    }

    // Скролл — реакция только на смену индекса; строка встаёт по центру видимой области
    LaunchedEffect(activeIndex, viewportHeightPx) {
        if (activeIndex < 0 || viewportHeightPx <= 0) return@LaunchedEffect
        val centerOffset = (viewportHeightPx / 2 - halfLinePx - centerLiftPx).coerceAtLeast(0)
        if (!hasCentered) {
            // Первое позиционирование — мгновенно, без «пролёта» через весь текст
            listState.scrollToItem(index = activeIndex, scrollOffset = -centerOffset)
            hasCentered = true
        } else {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -centerOffset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Мягкое растворение строк у верхнего и нижнего краёв: маска режет
            // прозрачность самого контента, поэтому фон под ним остаётся чистым.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.07f to Color.Black,
                        0.87f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), color = highlightColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Поиск текста в LRCLIB…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lyricsResult?.isInstrumental == true -> {
                Text(
                    "Инструментальный трек",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyricsResult != null && lyricsResult.lines.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height },
                    // Воздух до шапки и до нижней градиентной маски над контролами
                    contentPadding = PaddingValues(
                        top = if (lyricsResult.isSynced) 64.dp else 28.dp,
                        bottom = 136.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(
                        items = lyricsResult.lines,
                        key = { index, line -> "$index-${line.timeMs}" },
                    ) { index, line ->
                        LyricLineItem(
                            text = line.text.ifBlank { "♪" },
                            isSynced = lyricsResult.isSynced,
                            isActive = lyricsResult.isSynced && index == activeIndex,
                            indexDistance = if (lyricsResult.isSynced && activeIndex >= 0) {
                                kotlin.math.abs(index - activeIndex)
                            } else {
                                0
                            },
                            lineProgress = { activeProgress },
                            highlightColor = highlightColor,
                            onClick = if (lyricsResult.isSynced && line.timeMs >= 0) {
                                { onSeekToLine(line.timeMs) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error ?: "Текст не найден",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text("Повторить поиск текста")
                    }
                }
            }
        }
    }
}

/**
 * Одна строка текста в стиле Material 3 Expressive:
 * - физический размер шрифта одинаков у всех строк — никаких скачков лейаута;
 * - активная строка «вырастает» через graphicsLayer, получает Bold и мягкое свечение;
 * - пропетая часть — primary, непропетая — onSurface, между ними плавная кромка;
 * - неактивные строки приглушены, а далёкие — ещё и размыты;
 * - прогресс читает только активная строка, остальные не перерисовываются на опросе.
 */
@Composable
private fun LyricLineItem(
    text: String,
    isSynced: Boolean,
    isActive: Boolean,
    indexDistance: Int,
    lineProgress: () -> Float,
    highlightColor: Color,
    onClick: (() -> Unit)?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Прогресс читает только активная строка — остальные не подписаны на опрос плеера
    val fillTarget = if (isActive && isSynced) lineProgress().coerceIn(0f, 1f) else 0f
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "lyricFill",
    )

    // Иерархия строк: активная крупнее, соседние — средние, дальние — компактные.
    // Размер задаётся напрямую, без graphicsLayer-масштаба, чтобы текст не вылезал за край.
    val targetFontSize = when {
        !isSynced -> 24.sp
        isActive -> 24.sp
        indexDistance == 1 -> 18.sp
        else -> 16.sp
    }
    val targetLineHeight = when {
        !isSynced || isActive -> 34.sp
        indexDistance == 1 -> 26.sp
        else -> 23.sp
    }

    val lineAlpha by animateFloatAsState(
        targetValue = when {
            !isSynced -> 0.82f
            isActive -> 1f
            indexDistance == 1 -> 0.55f
            else -> 0.35f
        },
        animationSpec = tween(durationMillis = 260),
        label = "lyricAlpha",
    )

    // Неактивные строки — приглушённый onSurfaceVariant; несинхронизированный текст — onSurface.
    val lineColor = if (isSynced) onSurfaceVariant else onSurface

    // Базовый стиль строки: размер, вес и мягкое свечение активной строки.
    val lineStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = targetFontSize,
        lineHeight = targetLineHeight,
        letterSpacing = if (isActive) (-0.2).sp else 0.sp,
        fontWeight = when {
            isActive -> FontWeight.Bold
            !isSynced -> FontWeight.Normal
            indexDistance == 1 -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        shadow = if (isActive && isSynced) {
            Shadow(
                color = highlightColor.copy(alpha = 0.38f),
                blurRadius = 18f,
            )
        } else {
            null
        },
    )

    // Кисть обновляется вместе с анимацией заливки: слева — пропето, справа — ещё нет.
    val lyricsBrush = remember(fill, highlightColor, onSurface) {
        buildLyricsBrush(
            fillFraction = fill,
            sungColor = highlightColor,
            unsungColor = onSurface,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = lineStyle.copy(
                brush = if (isActive && isSynced) lyricsBrush else null,
            ),
            color = if (isActive && isSynced) Color.Unspecified else lineColor,
            // Без жёстких ограничений: длинные строки свободно переносятся
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = lineAlpha },
        )
    }
}

/**
 * Изолированная секция прогресса для «острова»:
 * таймлайн Material3 Slider + таймкоды.
 * Оставшееся время справа отображается с минусом ("-m:ss").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgressSection(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isResolving: Boolean = false,
    trackId: String? = null,
    durationMs: Long,
    primaryColor: Color,
    currentFraction: () -> Float,
    currentPositionMs: () -> Long,
    bufferedFraction: () -> Float = { 0f },
    onSeek: (Float) -> Unit,
) {
    var sliderState by remember(trackId) { mutableStateOf(PlayerSliderState()) }
    // Новый трек стартует с нуля: старые позиция и буфер не должны переезжать
    // в следующий трек — иначе не срабатывает морф индикатора загрузки.
    var playbackFraction by remember(trackId) { mutableFloatStateOf(0f) }
    var playbackPositionMs by remember(trackId) { mutableLongStateOf(0L) }
    var bufferFrac by remember(trackId) { mutableFloatStateOf(0f) }
    val latestTrackId by rememberUpdatedState(trackId)
    val latestFraction by rememberUpdatedState(currentFraction)
    val latestPosition by rememberUpdatedState(currentPositionMs)
    val latestBuffer by rememberUpdatedState(bufferedFraction)
    val latestSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(trackId) {
        while (true) {
            // Playback продолжает обновляться даже во время preview, чтобы Cancel был актуальным.
            playbackFraction = latestFraction()
            playbackPositionMs = latestPosition()
            bufferFrac = latestBuffer()
            delay(150)
        }
    }

    val displayPosition = sliderState.display(playbackFraction)
    val displayedMs = sliderState.preview?.let { (it * durationMs).toLong() } ?: playbackPositionMs

    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0L)
    // Индикатор загрузки живёт только на старте трека и не возвращается назад:
    // пауза, перемотка в начало и повторная буферизация не перезапускают морф.
    val isInitialBuffering = (isBuffering || isResolving) && displayedMs == 0L
    var hasStarted by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(trackId, isInitialBuffering) {
        if (!isInitialBuffering) hasStarted = true
    }
    val showLoading = isInitialBuffering && !hasStarted

    Column(modifier = Modifier.fillMaxWidth()) {
        Crossfade(
            targetState = showLoading,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "progressCrossfade",
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Высота совпадает со слайдером — лейаут не прыгает при Crossfade
                        .height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    )
                }
            } else {
                key(trackId) {
                    val gestureTrack = trackId
                    ExpressivePlayerSlider(
                        value = displayPosition.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            if (latestTrackId == gestureTrack) sliderState = sliderState.change(value)
                        },
                        onDragInteraction = { interaction ->
                            if (latestTrackId == gestureTrack) {
                                sliderState = when (interaction) {
                                    is DragInteraction.Start -> sliderState.start()
                                    is DragInteraction.Stop -> sliderState.stop()
                                    is DragInteraction.Cancel -> {
                                        playbackFraction = latestFraction()
                                        playbackPositionMs = latestPosition()
                                        sliderState.cancel()
                                    }
                                    else -> sliderState
                                }
                            }
                        },
                        onValueChangeFinished = {
                            // Cancel уже обработан тем же interactionSource и убрал preview.
                            if (latestTrackId == gestureTrack) {
                                sliderState.preview?.let { latestSeek(it) }
                                sliderState = sliderState.cancel()
                                playbackFraction = latestFraction()
                                playbackPositionMs = latestPosition()
                            }
                        },
                        activeColor = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        thumbColor = MaterialTheme.colorScheme.onSurface,
                        bufferedFraction = bufferFrac,
                        enabled = durationMs > 0,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(displayedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatMs(remainingMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Градиент караоке для всей строки:
 * слева — пропетая часть в акценте, справа — непропетая, между ними мягкая кромка.
 * Без явных start/end градиент растягивается по границам самой строки.
 */
private fun buildLyricsBrush(
    fillFraction: Float,
    sungColor: Color,
    unsungColor: Color,
): Brush {
    val fill = fillFraction.coerceIn(0f, 1f)
    if (fill <= 0f) {
        return SolidColor(unsungColor)
    }
    if (fill >= 1f) {
        return SolidColor(sungColor)
    }

    // Полуширина мягкой кромки в долях ширины строки
    val edge = 0.05f
    val edgeStart = (fill - edge).coerceAtLeast(0f)
    val edgeEnd = (fill + edge).coerceAtMost(1f)

    val stops = buildList {
        if (edgeStart > 0f) add(0f to sungColor)
        add(edgeStart to sungColor)
        add(edgeEnd to unsungColor)
        if (edgeEnd < 1f) add(1f to unsungColor)
    }
    // Горизонтальная заливка: перенесённая строка не становится серой справа.
    return Brush.horizontalGradient(colorStops = stops.toTypedArray())
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
