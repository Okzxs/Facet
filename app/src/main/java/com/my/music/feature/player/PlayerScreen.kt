package com.my.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.ui.components.ExpressivePlayerSlider
import com.my.music.core.ui.components.PlayerControlIsland
import com.my.music.core.ui.components.PlayerQueueSheet
import com.my.music.core.ui.theme.PlayerDynamicColors
import com.my.music.core.ui.theme.rememberPlayerDynamicColors
import com.my.music.data.model.LyricsResult
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Вариант компоновки hero-зоны (для сравнения на устройстве, поменяй цифру и пересобери):
 * 1 — «Редакция»: наклонённая обложка по центру, стеклянная карточка с огромным названием свисает с её нижнего края;
 * 2 — «Смещение»: обложка свисает за правый край экрана, карточка с названием — слева;
 * 3 — «На обложке»: название лежит прямо на обложке в матово-стеклянной плашке.
 */
private const val PLAYER_LAYOUT_VARIANT = 1

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
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    var isLyricsMode by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Текущий трек берём из PlayerController (он всегда актуален после play())
    val track = playerState.currentTrack ?: uiState.track

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
        Brush.verticalGradient(
            colors = listOf(
                animatedBgTop,
                animatedBgBottom,
                defaultSurface,
            ),
        )
    }

    val surfaceColor = playerColors.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        text = "СЕЙЧАС ИГРАЕТ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        // Явная непрозрачность: лейбл не растворяется на градиентном фоне
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ─── Hero-зона: обложка-задник + передний план (обложка/тексты) ──
                ExpressiveHeroZone(
                    variant = PLAYER_LAYOUT_VARIANT,
                    title = track?.title ?: "Нет трека",
                    artist = track?.artist ?: "—",
                    artworkUrl = track?.artworkUrl,
                    isLiked = isLiked,
                    isPlaying = playerState.isPlaying,
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
                    onToggleLike = { viewModel.toggleLike() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

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
    isPlaying: Boolean,
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
                    alpha = lerp(1f, 0.30f, recede)
                    translationY = heroScroll.value * 0.32f
                }
                .blur(
                    radius = (26f * recede).dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
        ) {
            // Свечение: размытое цветное облако палитры под обложкой
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.22f
                        scaleY = 1.22f
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
                    isPlaying = isPlaying,
                    error = lyricsError,
                    highlightColor = palette.primary,
                    glassColor = palette.surfaceContainerHigh,
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
                    primaryColor = palette.primary,
                    primaryContainerColor = palette.primaryContainer,
                    surfaceColor = palette.surfaceContainerHigh,
                    onToggleLike = onToggleLike,
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
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
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
                primaryColor = primaryColor,
                primaryContainerColor = primaryContainerColor,
                surfaceColor = surfaceColor,
                onToggleLike = onToggleLike,
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
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
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
 * Стеклянный экран текстов поверх «утопленной» размытой обложки:
 * компактная шапка-витрина + полупрозрачная панель со строками.
 */
@Composable
private fun LyricsForeground(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    isPlaying: Boolean,
    error: String?,
    highlightColor: Color,
    glassColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val miniShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        // Шапка-витрина: миниатюра + название/артист на матовом стекле
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 30.dp, bottomStart = 10.dp),
            // Плотнее стекло — выше контраст текста на размытом фоне
            color = glassColor.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = miniShape,
                    color = glassColor,
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
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Полупрозрачная панель строк — продолжение матового стекла
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = glassColor.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                LyricsListView(
                    lyricsResult = lyricsResult,
                    isLoading = isLoading,
                    isPlaying = isPlaying,
                    error = error,
                    highlightColor = highlightColor,
                    currentPositionMs = currentPositionMs,
                    onSeekToLine = onSeekToLine,
                    onRetry = onRetry,
                )
            }
        }

        // Воздух до острова управления, чтобы строки не налезали на язычок и волну
        Spacer(Modifier.height(18.dp))
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
 * Изолированный список строк текста:
 * - Экспрессивная караоке-строка: «жидкие чернила» с мягкой кромкой, тлеющим фронтом и шиммером
 * - Автоцентрирование активной строки по вертикали (как в Apple Music)
 * - Тап = перемотка
 */
@Composable
private fun LyricsListView(
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    isPlaying: Boolean,
    error: String?,
    highlightColor: Color,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    var activeIndex by remember { mutableIntStateOf(-1) }
    var activeProgress by remember { mutableFloatStateOf(0f) }

    // Центрирование активной строки, как в Apple Music:
    // измеряем видимую высоту списка и делаем симметричные отступы на полвысоты
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val halfLinePx = with(density) { 18.dp.roundToPx() }
    val centerPadding = with(density) {
        (viewportHeightPx / 2 - halfLinePx).coerceAtLeast(0).toDp()
    }

    LaunchedEffect(lyricsResult) {
        // Новый текст — с чистого листа: мгновенное центрирование первой строки
        activeIndex = -1
        activeProgress = 0f
        while (true) {
            val lines = lyricsResult?.lines
            if (lyricsResult?.isSynced == true && !lines.isNullOrEmpty()) {
                val curTime = currentPositionMs()
                val idx = lines.indexOfLast { it.timeMs <= curTime }
                if (idx != activeIndex) {
                    val previousIndex = activeIndex
                    activeIndex = idx
                    if (idx >= 0) {
                        // Активная строка встаёт ровно по центру видимой области
                        val centerOffset = (viewportHeightPx / 2 - halfLinePx).coerceAtLeast(0)
                        if (previousIndex < 0) {
                            // При открытии текстов — без «пролёта» через весь список
                            listState.scrollToItem(index = idx, scrollOffset = -centerOffset)
                        } else {
                            listState.animateScrollToItem(index = idx, scrollOffset = -centerOffset)
                        }
                    }
                }
                // Прогресс внутри активной строки для караоке-заливки
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

    Box(
        modifier = Modifier.fillMaxSize(),
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
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { viewportHeightPx = it.height },
                        // Симметричные отступы на полвысоты — строка может встать в самый центр
                        contentPadding = PaddingValues(vertical = centerPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = lyricsResult.lines,
                            key = { index, line -> "$index-${line.timeMs}" },
                        ) { index, line ->
                            LyricLineItem(
                                text = line.text.ifBlank { "♪" },
                                isSynced = lyricsResult.isSynced,
                                isActive = lyricsResult.isSynced && index == activeIndex,
                                isPast = lyricsResult.isSynced && index < activeIndex,
                                isPlaying = isPlaying,
                                karaokeProgress = { activeProgress },
                                highlightColor = highlightColor,
                                onClick = if (lyricsResult.isSynced && line.timeMs >= 0) {
                                    { onSeekToLine(line.timeMs) }
                                } else {
                                    null
                                },
                            )
                        }
                    }

                    Text(
                        text = if (lyricsResult.isSynced) "Синхронный текст · LRCLIB" else "Текст песни · LRCLIB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
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
 * Одна строка караоке. Вся Expressive-механика собрана здесь:
 * - «жидкие чернила»: заливка слева направо с мягкой кромкой и тлеющим фронтом в цвете палитры;
 * - шиммер-перелив, бегущий по строке, пока идёт воспроизведение;
 * - фокус от ОДНОЙ пружины: вес 500→700, масштаб 1.0→1.05, лёгкое свечение primary;
 * - прогресс заливки и шиммер читаются только активной строкой — остальные не перерисовываются.
 */
@Composable
private fun LyricLineItem(
    text: String,
    isSynced: Boolean,
    isActive: Boolean,
    isPast: Boolean,
    isPlaying: Boolean,
    karaokeProgress: () -> Float,
    highlightColor: Color,
    onClick: (() -> Unit)?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Единая пружина фокуса: ею живут вес, масштаб, свечение и альфы заливки.
    // dampingRatio 0.85 — благородное плавное увеличение без подпрыгивания
    val focus by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricLineFocus",
    )

    // Прогресс караоке читается ТОЛЬКО активной строкой:
    // остальные не подписаны на 150-мс опрос позиции плеера
    val fillTarget = when {
        !isSynced -> 1f
        isActive -> karaokeProgress()
        isPast -> 1f
        else -> 0f
    }
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "lyricLineFill",
    )

    // Перелив включается только у активной строки и только во время воспроизведения
    val shimmerPhase = if (isActive && isPlaying) {
        val shimmer = rememberInfiniteTransition(label = "lyricLineShimmer")
        shimmer.animateFloat(
            initialValue = KARAOKE_SHIMMER_FROM,
            targetValue = KARAOKE_SHIMMER_TO,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "lyricLineShimmerPhase",
        ).value
    } else {
        0f
    }

    var textWidth by remember { mutableIntStateOf(0) }

    // Альфы ведёт фокус: неактивные строки — 0.4, у активной «непропетое» — 0.62, «пропетое» — 1.0
    val filledColor = onSurface.copy(alpha = lerp(0.40f, 1f, focus))
    val dimColor = onSurface.copy(alpha = lerp(0.40f, 0.62f, focus))
    // Фронт чернил приглушён (alpha 0.7) — тлеет мягко, а не горит
    val tipColor = blendColors(filledColor, highlightColor, 0.75f).copy(alpha = 0.7f)
    val shimmerColor = blendColors(filledColor, Color.White, 0.65f)
    val shimmerStrength = if (isActive && isPlaying) 1f else 0f

    val karaokeBrush = remember(
        fill, focus, textWidth, shimmerPhase, shimmerStrength, shimmerColor, tipColor, filledColor, dimColor,
    ) {
        buildKaraokeBrush(
            fillFraction = fill,
            textWidth = textWidth,
            filledColor = filledColor,
            dimColor = dimColor,
            tipColor = tipColor,
            shimmerColor = shimmerColor,
            shimmerPhase = shimmerPhase,
            shimmerStrength = shimmerStrength,
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight(lerp(500f, 700f, focus).toInt()),
            fontSize = if (isSynced) 22.sp else 18.sp,
            shadow = Shadow(
                color = highlightColor.copy(alpha = 0.2f * focus),
                blurRadius = lerp(2f, 8f, focus),
            ),
            brush = if (isSynced) karaokeBrush else null,
        ),
        color = if (isSynced) Color.Unspecified else onSurface.copy(alpha = 0.85f),
        onTextLayout = { layoutResult ->
            val width = if (layoutResult.lineCount > 0) {
                layoutResult.multiParagraph.getLineWidth(0).toInt()
            } else {
                0
            }
            if (width != textWidth) textWidth = width
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Рост строки от левого края — позиция чтения не «уезжает»
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = lerp(1f, 1.05f, focus)
                scaleY = lerp(1f, 1.05f, focus)
            }
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .padding(vertical = 4.dp),
    )
}

/**
 * Изолированная секция прогресса для «острова»:
 * цепочка точек ExpressivePlayerSlider + таймкоды.
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
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var bufferFrac by remember { mutableFloatStateOf(0f) }

    // Сброс таймкода и слайдера при смене трека
    LaunchedEffect(trackId) {
        playbackFraction = 0f
        playbackPositionMs = 0L
        dragPosition = 0f
        isDragging = false
        bufferFrac = 0f
    }

    LaunchedEffect(isPlaying, trackId) {
        while (true) {
            if (!isDragging) {
                playbackFraction = currentFraction()
                playbackPositionMs = currentPositionMs()
            }
            bufferFrac = bufferedFraction()
            delay(150) // ~6-7 обновлений в секунду
        }
    }

    val displayPosition = if (isDragging) dragPosition else playbackFraction
    val displayedMs = if (isDragging) {
        (dragPosition * durationMs).toLong()
    } else {
        playbackPositionMs
    }

    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0L)
    // Пока идёт резолв потока/буферизация и позиция == 0 — показываем индикатор загрузки,
    // при старте воспроизведения Crossfade (500 мс) плавно заменяет его на слайдер.
    val isInitialBuffering = (isBuffering || isResolving) && displayedMs == 0L

    Column(modifier = Modifier.fillMaxWidth()) {
        Crossfade(
            targetState = isInitialBuffering,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "progressCrossfade",
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
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
                ExpressivePlayerSlider(
                    value = displayPosition.coerceIn(0f, 1f),
                    onValueChange = { value ->
                        isDragging = true
                        dragPosition = value
                    },
                    onValueChangeFinished = { value ->
                        onSeek(value)
                        isDragging = false
                    },
                    activeColor = primaryColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    bufferedFraction = bufferFrac,
                )
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

// ─── Геометрия «жидких чернил» ────────────────────────────────
private const val KARAOKE_EDGE = 0.028f // полуширина мягкой кромки заливки
private const val KARAOKE_SHIMMER_HALF = 0.12f // полуширина бегущего перелива (шире — мягче)
private const val KARAOKE_SHIMMER_FROM = -0.15f // старт перелива (за левым краем)
private const val KARAOKE_SHIMMER_TO = 1.15f // финиш перелива (за правым краем)

/** Мягкое смешение двух цветов с клампом доли. */
private fun blendColors(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

/** Smoothstep-переход между цветами вокруг кромки заливки. */
private fun smoothBlend(from: Color, to: Color, at: Float, x: Float, feather: Float): Color {
    if (at <= 0f) return from
    if (at >= 1f) return to
    val t = ((x - (at - feather)) / (2f * feather)).coerceIn(0f, 1f)
    return blendColors(from, to, t * t * (3f - 2f * t))
}

/** Цвет строки в точке x (0..1): основа с кромкой + тлеющий фронт + перелив. */
private fun karaokeColorAt(
    x: Float,
    fill: Float,
    shimmer: Float,
    filledColor: Color,
    dimColor: Color,
    tipColor: Color,
    shimmerColor: Color,
    shimmerStrength: Float,
): Color {
    // Основа: мягкая кромка «пропето / не пропето»
    val base = smoothBlend(dimColor, filledColor, fill, x, KARAOKE_EDGE)
    // Тлеющий фронт чернил точно на границе заливки
    val tip = if (fill > 0.001f && fill < 0.999f) {
        (1f - abs(x - fill) / (KARAOKE_EDGE * 1.8f)).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Перелив — едва заметный блик, скользящий по строке
    val shimmerAmount = (1f - abs(x - shimmer) / KARAOKE_SHIMMER_HALF)
        .coerceIn(0f, 1f) * shimmerStrength * 0.2f

    return blendColors(
        blendColors(base, tipColor, tip * 0.65f),
        shimmerColor,
        shimmerAmount,
    )
}

/** Градиент караоке для Text: заливка, кромка, тлеющий фронт и бегущий перелив. */
private fun buildKaraokeBrush(
    fillFraction: Float,
    textWidth: Int,
    filledColor: Color,
    dimColor: Color,
    tipColor: Color,
    shimmerColor: Color,
    shimmerPhase: Float,
    shimmerStrength: Float,
): Brush {
    val width = textWidth.coerceAtLeast(1).toFloat()
    val fill = fillFraction.coerceIn(0f, 1f)

    // Критические точки градиента: кромка заливки + фронт перелива
    val stops = buildList {
        add(0f)
        add(1f)
        add(fill - KARAOKE_EDGE)
        add(fill)
        add(fill + KARAOKE_EDGE)
        add(shimmerPhase - KARAOKE_SHIMMER_HALF)
        add(shimmerPhase)
        add(shimmerPhase + KARAOKE_SHIMMER_HALF)
    }.map { it.coerceIn(0f, 1f) }
        .distinct()
        .sorted()
        .map { x ->
            x to karaokeColorAt(
                x = x,
                fill = fill,
                shimmer = shimmerPhase,
                filledColor = filledColor,
                dimColor = dimColor,
                tipColor = tipColor,
                shimmerColor = shimmerColor,
                shimmerStrength = shimmerStrength,
            )
        }
        .toTypedArray()

    return Brush.linearGradient(
        colorStops = stops,
        start = Offset.Zero,
        end = Offset(width, 0f),
    )
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
