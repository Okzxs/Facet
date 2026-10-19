package com.my.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.ui.components.PlayerQueueSheet
import com.my.music.core.ui.components.PlayerSlider
import com.my.music.core.ui.theme.PlayerDynamicColors
import com.my.music.core.ui.theme.rememberPlayerDynamicColors
import com.my.music.data.model.LyricLine
import com.my.music.data.model.LyricsResult
import kotlinx.coroutines.delay

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
    val context = LocalContext.current

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

    // ─── Тонированный фон и палитра по референсу (Palette) ───────────────
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultOnPrimary = MaterialTheme.colorScheme.onPrimary
    val defaultPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val defaultOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val playerColors = rememberPlayerDynamicColors(
        artworkUrl = track?.artworkUrl,
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // CenterAlignedTopAppBar центрирует заголовок между «Назад» и «Меню» с учётом их ширины
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
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = track?.title ?: "MyMusic",
                            style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ─── Анимированное переключение «Обложка ↔ Текст» ─────────────
                val surfaceColor = playerColors.surfaceContainerHigh
                val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
                val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

                AnimatedContent(
                    targetState = isLyricsMode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        if (targetState) {
                            // Обложка → Текст: список въезжает снизу, обложка уходит вверх
                            (slideInVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            ) { it / 3 } + fadeIn(tween(durationMillis = 240))) togetherWith
                                (fadeOut(tween(durationMillis = 180)) + slideOutVertically(
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                ) { -it / 4 })
                        } else {
                            // Текст → Обложка: зеркальный переход
                            (slideInVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                            ) { -it / 4 } + fadeIn(tween(durationMillis = 240))) togetherWith
                                (fadeOut(tween(durationMillis = 180)) + slideOutVertically(
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                                ) { it / 3 })
                        }
                    },
                    label = "playerModeContent",
                ) { lyricsMode ->
                    if (lyricsMode) {
                        LyricsModeContent(
                            title = track?.title ?: "—",
                            artist = track?.artist ?: "—",
                            artworkUrl = track?.artworkUrl,
                            lyricsResult = uiState.lyrics,
                            isLoading = uiState.isLyricsLoading,
                            error = uiState.lyricsError,
                            highlightColor = playerColors.primary,
                            surfaceContainerColor = surfaceColor,
                            notePainter = notePainter,
                            placeholderPainter = placeholderPainter,
                            currentPositionMs = { viewModel.currentPositionMs() },
                            onSeekToLine = { timeMs -> viewModel.seekToMs(timeMs) },
                            onRetry = { viewModel.loadLyricsIfNeeded(track) },
                        )
                    } else {
                        CoverModeContent(
                            title = track?.title ?: "Нет трека",
                            artist = track?.artist ?: "—",
                            artworkUrl = track?.artworkUrl,
                            isLiked = isLiked,
                            primaryColor = playerColors.primary,
                            primaryContainerColor = playerColors.primaryContainer,
                            surfaceContainerColor = surfaceColor,
                            notePainter = notePainter,
                            placeholderPainter = placeholderPainter,
                            onToggleLike = { viewModel.toggleLike() },
                        )
                    }
                }

                // ─── Сообщение об ошибке (скрывается, как только плеер в STATE_READY) ──
                val isPlayerReady = playerState.playbackState == androidx.media3.common.Player.STATE_READY
                val errorMessage = if (isPlayerReady) null else (uiState.error ?: playerState.error)

                if (errorMessage != null) {
                    Text(
                        text = "⚠️ $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // ─── Слайдер прогресса, буферизация и время ("-m:ss") ─────────
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

                Spacer(Modifier.height(10.dp))

                // ─── Кнопки управления (ControlsRow: круги 56dp, Play 84dp с morph) ──
                ControlsRow(
                    isPlaying = playerState.isPlaying,
                    isBuffering = isBuffering,
                    primaryColor = playerColors.primary,
                    onPrimaryColor = playerColors.onPrimary,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onPrev = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                )

                Spacer(Modifier.height(16.dp))

                // ─── Пилюли действий (1× Скорость / Текст / Далее) ────────────
                ActionPillsRow(
                    playbackSpeed = playerState.playbackSpeed,
                    isLyricsActive = isLyricsMode,
                    activeContainerColor = playerColors.secondaryContainer,
                    activeContentColor = playerColors.onSecondaryContainer,
                    surfaceContainerColor = playerColors.surfaceContainerHigh,
                    onSpeedClick = { viewModel.cyclePlaybackSpeed() },
                    onLyricsClick = {
                        val nextMode = !isLyricsMode
                        isLyricsMode = nextMode
                        if (nextMode) {
                            viewModel.loadLyricsIfNeeded(track)
                        }
                    },
                    onQueueClick = { showQueueSheet = true },
                )

                Spacer(Modifier.height(16.dp))
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
 * Режим обложки: крупная обложка (248dp) со свечением и лёгким параллаксом,
 * под ней — название, артист и лайк-сквиркл. Вся зона скроллится вертикально.
 */
@Composable
private fun CoverModeContent(
    title: String,
    artist: String,
    artworkUrl: String?,
    isLiked: Boolean,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceContainerColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    onToggleLike: () -> Unit,
) {
    val coverScroll = rememberScrollState()
    val artworkShape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(coverScroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(248.dp)
                // Параллакс: обложка отстаёт от скролла текста под ней
                .graphicsLayer { translationY = coverScroll.value * 0.45f },
            contentAlignment = Alignment.Center,
        ) {
            // Свечение позади обложки: рисуется крупнее, но layout не раздувает
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.16f
                        scaleY = 1.16f
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                        ),
                        shape = CircleShape,
                    ),
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 18.dp,
                        shape = artworkShape,
                        clip = false,
                        ambientColor = primaryColor.copy(alpha = 0.55f),
                        spotColor = primaryColor,
                    )
                    .clip(artworkShape),
                shape = artworkShape,
                color = surfaceContainerColor,
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = artworkShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(16.dp))

            // Лайк-сквиркл (RoundedCornerShape 18dp)
            FilledTonalIconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isLiked) primaryContainerColor else surfaceContainerColor,
                    contentColor = if (isLiked) primaryColor else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Лайк",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Режим текста: компактный ряд (миниатюра 56dp + название/артист) и список строк текста.
 */
@Composable
private fun LyricsModeContent(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    error: String?,
    highlightColor: Color,
    surfaceContainerColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val artworkShape = RoundedCornerShape(16.dp)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(elevation = 6.dp, shape = artworkShape, clip = false)
                    .clip(artworkShape),
                shape = artworkShape,
                color = surfaceContainerColor,
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = artworkShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
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
    }
}

/** Пружинный press-scale (0.90 при нажатии) для expressive-кнопок. */
@Composable
private fun rememberPressScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

/**
 * Изолированный список строк текста:
 * - Легкое свечение активной строки (Shadow blur 8dp)
 * - Автоскролл через animateScrollToItem(index, tween(250))
 * - Тап = перемотка
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

    LaunchedEffect(lyricsResult) {
        while (true) {
            val lines = lyricsResult?.lines
            if (lyricsResult?.isSynced == true && !lines.isNullOrEmpty()) {
                val curTime = currentPositionMs()
                val idx = lines.indexOfLast { it.timeMs <= curTime }
                if (idx != activeIndex) {
                    activeIndex = idx
                    if (idx >= 0) {
                        listState.animateScrollToItem(
                            index = (idx - 2).coerceAtLeast(0),
                            scrollOffset = 0,
                        )
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
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = lyricsResult.lines,
                            key = { index, line -> "$index-${line.timeMs}" },
                        ) { index, line ->
                            val isActive = lyricsResult.isSynced && index == activeIndex
                            val isSynced = lyricsResult.isSynced
                            val onSurface = MaterialTheme.colorScheme.onSurface
                            val filledColor = if (isActive) onSurface else onSurface.copy(alpha = 0.55f)
                            val dimColor = onSurface.copy(alpha = 0.30f)

                            // Караоке-эффект: заливка строки слева направо по мере проигрывания
                            val targetFill = when {
                                !isSynced -> 1f
                                index < activeIndex -> 1f
                                index == activeIndex -> activeProgress
                                else -> 0f
                            }
                            val fill by animateFloatAsState(
                                targetValue = targetFill,
                                animationSpec = tween(durationMillis = 160, easing = LinearEasing),
                                label = "lyricsFill",
                            )
                            var textWidth by remember { mutableIntStateOf(0) }
                            val karaokeBrush = remember(fill, textWidth, filledColor, dimColor) {
                                buildKaraokeBrush(fill, textWidth, filledColor, dimColor)
                            }

                            val animatedGlow by animateColorAsState(
                                targetValue = if (isActive) highlightColor.copy(alpha = 0.5f) else Color.Transparent,
                                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                                label = "lyricsLineGlow",
                            )
                            val animatedWeight by animateFloatAsState(
                                targetValue = if (isActive) 700f else 400f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                label = "lyricsLineWeight",
                            )
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isActive) 1.05f else 1f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                label = "lyricsLineScale",
                            )

                            Text(
                                text = line.text.ifBlank { "♪" },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight(animatedWeight.toInt()),
                                    fontSize = if (isSynced) 22.sp else 18.sp,
                                    shadow = Shadow(
                                        color = animatedGlow,
                                        blurRadius = 10f,
                                    ),
                                    brush = if (isSynced) karaokeBrush else null,
                                ),
                                color = if (isSynced) Color.Unspecified else onSurface.copy(alpha = 0.92f),
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
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .clickable(
                                        enabled = isSynced && line.timeMs >= 0,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onSeekToLine(line.timeMs) },
                                    )
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = if (lyricsResult.isSynced) "Синхронный текст · LRCLIB" else "Текст песни · LRCLIB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
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
 * Панель кнопок воспроизведения по референсу:
 * - Play/Pause: морф радиуса (42dp в паузе / 30dp в воспроизведении) и press-scale
 * - Prev / Next: залитые круги 56dp (фон primary, иконка onSurface)
 */
@Composable
private fun ControlsRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    primaryColor: Color,
    onPrimaryColor: Color,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val playShapeRadius by animateDpAsState(
        targetValue = if (isPlaying) 30.dp else 42.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "playMorphRadius",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val prevInteraction = remember { MutableInteractionSource() }
        val prevScale = rememberPressScale(prevInteraction)

        // Prev: Залитый круг 56dp
        FilledIconButton(
            onClick = onPrev,
            interactionSource = prevInteraction,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = prevScale
                    scaleY = prevScale
                },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = primaryColor.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Предыдущий",
                modifier = Modifier.size(28.dp),
            )
        }

        val playInteraction = remember { MutableInteractionSource() }
        val playScale = rememberPressScale(playInteraction)

        // Главная кнопка Play/Pause с динамическим радиусом (84dp)
        FilledIconButton(
            onClick = onPlayPause,
            interactionSource = playInteraction,
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                },
            shape = RoundedCornerShape(playShapeRadius),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = primaryColor,
                contentColor = onPrimaryColor,
            ),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = onPrimaryColor,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        val nextInteraction = remember { MutableInteractionSource() }
        val nextScale = rememberPressScale(nextInteraction)

        // Next: Залитый круг 56dp
        FilledIconButton(
            onClick = onNext,
            interactionSource = nextInteraction,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = nextScale
                    scaleY = nextScale
                },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = primaryColor.copy(alpha = 0.25f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Следующий",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Пилюли действий (высота 46dp).
 */
@Composable
private fun ActionPillsRow(
    playbackSpeed: Float,
    isLyricsActive: Boolean,
    activeContainerColor: Color,
    activeContentColor: Color,
    surfaceContainerColor: Color,
    onSpeedClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val speedText = if (playbackSpeed == 1.0f) "1×" else "${playbackSpeed}×"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Левая пилюля — Скорость
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onSpeedClick),
            shape = RoundedCornerShape(topStart = 23.dp, bottomStart = 23.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            color = surfaceContainerColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Центральная пилюля — Текст
        val lyricsBgColor by animateColorAsState(
            targetValue = if (isLyricsActive) activeContainerColor else surfaceContainerColor,
            animationSpec = tween(durationMillis = 200),
            label = "lyricsBgColor",
        )
        val lyricsTextColor = if (isLyricsActive) activeContentColor else MaterialTheme.colorScheme.onSurface

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onLyricsClick),
            shape = RoundedCornerShape(8.dp),
            color = lyricsBgColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Текст",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = lyricsTextColor,
                )
            }
        }

        // Правая пилюля — Очередь / Далее
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onQueueClick),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 23.dp, bottomEnd = 23.dp),
            color = surfaceContainerColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Далее",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Изолированная секция слайдера прогресса и времени.
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = isInitialBuffering,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "progressCrossfade",
            ) { loading ->
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = primaryColor,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    }
                } else {
                    // Expressive-слайдер: толстая дорожка, буфер, пружинный ползунок
                    PlayerSlider(
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
                        modifier = Modifier.fillMaxWidth(),
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatMs(remainingMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Градиентная кисть караоке: заполненная часть строки слева, тусклая — справа. */
private fun buildKaraokeBrush(
    fillFraction: Float,
    textWidth: Int,
    filledColor: Color,
    dimColor: Color,
): Brush {
    val width = textWidth.coerceAtLeast(1).toFloat()
    val fill = fillFraction.coerceIn(0f, 1f)
    val edge = 0.05f
    val colorStops: Array<Pair<Float, Color>> = when {
        fill <= 0f -> arrayOf(0f to dimColor, 1f to dimColor)
        fill >= 1f -> arrayOf(0f to filledColor, 1f to filledColor)
        else -> arrayOf(
            0f to filledColor,
            fill to filledColor,
            (fill + edge).coerceAtMost(1f) to dimColor,
            1f to dimColor,
        )
    }
    return Brush.linearGradient(
        colorStops = colorStops,
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
