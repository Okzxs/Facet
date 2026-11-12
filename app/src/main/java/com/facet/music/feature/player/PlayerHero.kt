package com.facet.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateFormat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.download.DownloadState
import com.facet.music.core.location.LocationPermissions
import com.facet.music.core.ui.components.ExpressivePlayerSlider
import com.facet.music.core.ui.components.PlayerControlIsland
import com.facet.music.core.ui.components.PlayerQueueSheet
import com.facet.music.core.ui.components.PlayerSliderState
import com.facet.music.core.ui.components.AddToPlaylistSheet
import com.facet.music.core.ui.components.SleepTimerSheet
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.core.ui.theme.bestContentColor
import com.facet.music.core.ui.theme.contrastContentColor
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.facet.music.core.ui.theme.PlayerDynamicColors
import com.facet.music.core.ui.theme.rememberPlayerDynamicColors
import com.facet.music.core.player.PlaybackTuning
import com.facet.music.core.player.PlaybackVariation
import com.facet.music.data.model.LyricsResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.isMetadataOnly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

/**
 * Вариант компоновки hero-зоны (для сравнения на устройстве, поменяй цифру и пересобери):
 * 1 — «Редакция»: обложка по центру, стеклянная карточка с огромным названием свисает с её нижнего края;
 * 2 — «Смещение»: обложка свисает за правый край экрана, карточка с названием — слева;
 * 3 — «На обложке»: название лежит прямо на обложке в матово-стеклянной плашке.
 */
internal const val PLAYER_LAYOUT_VARIANT = 1

/** Свайп по обложке: порог переключения трека — 30% ширины, скорость — 1000 px/s. */
internal const val SWITCH_SHARE = 0.30f

internal const val SWITCH_VELOCITY_PX_S = 1000f

/** Доля ширины, после которой показывается HUD направления переключения. */
internal const val SWIPE_PREVIEW_SHARE = 0.15f

/** Параметры компоновки для конкретного варианта. */
private data class HeroStyle(
    val widthFraction: Float,
    val alignment: Alignment,
    val topPadding: Dp,
    val offsetX: Dp,
    val overlap: Dp,
    val titleOnArtwork: Boolean,
)

private fun heroStyle(variant: Int): HeroStyle = when (variant) {
    2 -> HeroStyle(0.62f, Alignment.TopEnd, 26.dp, 16.dp, 60.dp, false)
    3 -> HeroStyle(0.94f, Alignment.TopCenter, 4.dp, 0.dp, 0.dp, true)
    else -> HeroStyle(0.88f, Alignment.TopCenter, 6.dp, 0.dp, 46.dp, false)
}

/**
 * Hero-зона плеера: обложка, название и артист. Смена трека анимируется
 * мягким fade + slide, тексты и обложка меняются одним блоком.
 */
@Composable
internal fun ExpressiveHeroZone(
    variant: Int,
    trackKey: String?,
    title: String,
    artist: String,
    source: Source?,
    artworkUrl: String?,
    isLiked: Boolean,
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    isLyricsMode: Boolean,
    lyricsData: LyricsData?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    lyricsListState: LazyListState,
    palette: PlayerDynamicColors,
    // Цвета кнопок под обложкой: акцент обложки/темы, его контейнер и подложка покоя.
    actionAccent: Color,
    actionContainer: Color,
    actionIdleContainer: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    isPlaying: Boolean,
    playbackClock: () -> Long,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    previousArtworkUrl: String?,
    nextArtworkUrl: String?,
    onArtworkBoundsChanged: (Rect?) -> Unit,
    isDismissDragging: Boolean = false,
    onSeekToLine: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onLyricsListBoundsChanged: (Rect?) -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
    /** Тап по имени артиста; null — подпись не кликабельна. */
    onArtistClick: (() -> Unit)? = null,
    coverModifier: Modifier = Modifier,
    coverSwipeModifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val heroScroll = rememberScrollState()
    val artworkShape = MaterialTheme.shapes.extraLarge
    val display = remember(trackKey, title, artist, artworkUrl) {
        TrackDisplay(trackKey, title, artist, artworkUrl)
    }

    // Переключение трека горизонтальным свайпом с прямой манипуляцией:
    // обложка едет за пальцем, соседи выезжают по краям, порог — 30% ширины
    // или скорость 1000 px/s, отпускание — пружина spatial.
    val haptics = LocalHapticFeedback.current
    val heroScope = rememberCoroutineScope()
    var swipeDirection by remember(trackKey) { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swipeVelocityTracker = remember { VelocityTracker() }
    var dragSettleJob by remember { mutableStateOf<Job?>(null) }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = isLyricsMode,
            modifier = Modifier
                .then(panelModifier)
                .then(if (isLyricsMode) Modifier.fillMaxSize() else Modifier),
            transitionSpec = {
                fadeIn(tween(durationMillis = 220)) togetherWith
                    fadeOut(tween(durationMillis = 160))
            },
            label = "playerModeContent",
        ) { lyricsMode ->
            if (lyricsMode) {
                LyricsForeground(
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    lyricsData = lyricsData,
                    isLoading = isLyricsLoading,
                    error = lyricsError,
                    listState = lyricsListState,
                    highlightColor = MaterialTheme.colorScheme.primary,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                    isPlaying = isPlaying,
                    playbackClock = playbackClock,
                    onSeekToLine = onSeekToLine,
                    onRetry = onRetryLyrics,
                    onListBoundsChanged = onLyricsListBoundsChanged,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(heroScroll)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(
                        targetState = display,
                        transitionSpec = {
                            ((fadeIn(tween(durationMillis = 340)) +
                                slideInVertically(tween(durationMillis = 340)) { it / 12 }) togetherWith
                                (fadeOut(tween(durationMillis = 220)) +
                                    slideOutVertically(tween(durationMillis = 220)) { -it / 12 }))
                                .using(SizeTransform(clip = false))
                        },
                        contentAlignment = Alignment.TopCenter,
                        label = "trackSwitch",
                    ) { state ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .aspectRatio(1f)
                                    .clip(artworkShape)
                                    .then(coverSwipeModifier)
                                    .onGloballyPositioned {
                                        // Во время закрытия обложка едет/сжимается:
                                        // не дёргаем границы на каждый кадр.
                                        if (!isDismissDragging) {
                                            onArtworkBoundsChanged(it.boundsInRoot())
                                        }
                                    }
                                    .pointerInput(trackKey) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                dragSettleJob?.cancel()
                                                swipeVelocityTracker.resetTracking()
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeVelocityTracker.addPosition(
                                                    change.uptimeMillis,
                                                    change.position,
                                                )
                                                val limit = size.width.toFloat()
                                                dragX = (dragX + dragAmount).coerceIn(-limit, limit)
                                                val preview = limit * SWIPE_PREVIEW_SHARE
                                                val direction = when {
                                                    dragX <= -preview -> -1
                                                    dragX >= preview -> 1
                                                    else -> 0
                                                }
                                                if (direction != swipeDirection) {
                                                    swipeDirection = direction
                                                    if (direction != 0) {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove,
                                                        )
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                val velocityX = swipeVelocityTracker.calculateVelocity().x
                                                val width = size.width.toFloat()
                                                val passed = abs(dragX) > width * SWITCH_SHARE ||
                                                    abs(velocityX) > SWITCH_VELOCITY_PX_S
                                                val goingNext = dragX < 0f || velocityX < 0f
                                                dragSettleJob = heroScope.launch {
                                                    if (passed) {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove,
                                                        )
                                                        animate(
                                                            initialValue = dragX,
                                                            targetValue = if (goingNext) -width else width,
                                                            animationSpec = Motion.spatialFast(),
                                                        ) { value, _ -> dragX = value }
                                                        if (goingNext) onNext() else onPrevious()
                                                        dragX = 0f
                                                    } else {
                                                        animate(
                                                            initialValue = dragX,
                                                            targetValue = 0f,
                                                            animationSpec = Motion.spatialDefault(),
                                                        ) { value, _ -> dragX = value }
                                                    }
                                                    swipeDirection = 0
                                                }
                                            },
                                            onDragCancel = {
                                                dragSettleJob = heroScope.launch {
                                                    animate(
                                                        initialValue = dragX,
                                                        targetValue = 0f,
                                                        animationSpec = Motion.spatialDefault(),
                                                    ) { value, _ -> dragX = value }
                                                    swipeDirection = 0
                                                }
                                            },
                                        )
                                    },
                            ) {
                                DisposableEffect(trackKey) {
                                    onDispose { onArtworkBoundsChanged(null) }
                                }
                                // Соседний трек справа (следующий): выезжает по краю за пальцем.
                                if (nextArtworkUrl != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationX = dragX + this.size.width
                                                alpha = if (dragX < -1f) 1f else 0f
                                            },
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = artworkShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 0.dp,
                                        ) {
                                            ArtworkImage(
                                                artworkUrl = nextArtworkUrl,
                                                shape = artworkShape,
                                                notePainter = notePainter,
                                                placeholderPainter = placeholderPainter,
                                            )
                                        }
                                    }
                                }
                                // Соседний трек слева (предыдущий).
                                if (previousArtworkUrl != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationX = dragX - this.size.width
                                                alpha = if (dragX > 1f) 1f else 0f
                                            },
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = artworkShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 0.dp,
                                        ) {
                                            ArtworkImage(
                                                artworkUrl = previousArtworkUrl,
                                                shape = artworkShape,
                                                notePainter = notePainter,
                                                placeholderPainter = placeholderPainter,
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = dragX }
                                        // Морф в мини-плеер — только на актуальном треке:
                                        // уходящий кадр не должен дублировать shared-element.
                                        .then(if (state.key == trackKey) coverModifier else Modifier),
                                    shape = artworkShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 0.dp,
                                ) {
                                    ArtworkImage(
                                        artworkUrl = state.artworkUrl,
                                        shape = artworkShape,
                                        notePainter = notePainter,
                                        placeholderPainter = placeholderPainter,
                                    )
                                }
                                // HUD переключения: какой трек сейчас будет запущен.
                                Crossfade(
                                    targetState = swipeDirection,
                                    modifier = Modifier.align(Alignment.Center),
                                    animationSpec = tween(140),
                                    label = "trackSwipeHud",
                                ) { direction ->
                                    if (direction != 0) {
                                        Surface(
                                            shape = MaterialTheme.shapes.large,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                .copy(alpha = 0.94f),
                                            tonalElevation = 4.dp,
                                            shadowElevation = 10.dp,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 18.dp,
                                                    vertical = 12.dp,
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = if (direction < 0) {
                                                        Icons.Filled.SkipNext
                                                    } else {
                                                        Icons.Filled.SkipPrevious
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(
                                                        if (direction < 0) {
                                                            R.string.player_swipe_next
                                                        } else {
                                                            R.string.player_swipe_previous
                                                        },
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            // Имя артиста — точка входа на его экран.
                            val artistClick = onArtistClick
                            val artistModifier = if (artistClick != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { artistClick() }
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            Text(
                                text = state.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = artistModifier,
                            )
                            if (source != null && !source.isMetadataOnly) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.player_source, sourceLabel(source)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canDownload) {
                            DownloadSquircle(
                                state = downloadState,
                                primaryColor = actionAccent,
                                primaryContainerColor = actionContainer,
                                idleContainerColor = actionIdleContainer,
                                onClick = onDownloadClick,
                            )
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        LikeSquircle(
                            isLiked = isLiked,
                            primaryColor = actionAccent,
                            primaryContainerColor = actionContainer,
                            idleContainerColor = actionIdleContainer,
                            onToggleLike = onToggleLike,
                        )
                        DislikeSquircle(
                            isDisliked = isDisliked,
                            idleContainerColor = actionIdleContainer,
                            onToggleDislike = onToggleDislike,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
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
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
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
                // Слот скачивания сохраняет ширину даже для локального трека:
                // иначе лайк и дизлайк прыгают между разными композициями.
                if (canDownload) {
                    DownloadSquircle(
                        state = downloadState,
                        primaryColor = primaryColor,
                        primaryContainerColor = primaryContainerColor,
                        onClick = onDownloadClick,
                    )
                } else {
                    Spacer(Modifier.size(46.dp))
                }
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
                Spacer(Modifier.width(8.dp))
                DislikeSquircle(
                    isDisliked = isDisliked,
                    onToggleDislike = onToggleDislike,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            TrackGlassCard(
                title = title,
                artist = artist,
                isLiked = isLiked,
                isDisliked = isDisliked,
                canDownload = canDownload,
                downloadState = downloadState,
                primaryColor = primaryColor,
                primaryContainerColor = primaryContainerColor,
                surfaceColor = surfaceColor,
                onToggleLike = onToggleLike,
                onToggleDislike = onToggleDislike,
                onDownloadClick = onDownloadClick,
                modifier = Modifier.offset(y = -style.overlap),
            )
        }

        // Гарантированный воздух между карточкой и островом управления
        Spacer(Modifier.height(24.dp))
    }
}

/** Матово-стеклянная карточка трека с огромной Display-типографикой. */
@Composable
private fun TrackGlassCard(
    title: String,
    artist: String,
    isLiked: Boolean,
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(26.dp)) {
            // Крупная, но спокойная типографика: 30sp SemiBold вместо 40sp ExtraBold
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                // Явный цвет: contentColorFor полупрозрачной «стеклянной» карточки
                // давал чёрный текст на тёмном фоне при светлых обложках.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
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
                Spacer(Modifier.width(12.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Резерв под кнопку скачивания: без него ряд действий «прыгает»
                // у локальных треков, которые уже лежат на диске.
                if (canDownload) {
                    DownloadSquircle(
                        state = downloadState,
                        primaryColor = primaryColor,
                        primaryContainerColor = primaryContainerColor,
                        onClick = onDownloadClick,
                    )
                } else {
                    Spacer(Modifier.size(46.dp))
                }
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
                Spacer(Modifier.width(8.dp))
                DislikeSquircle(
                    isDisliked = isDisliked,
                    onToggleDislike = onToggleDislike,
                )
            }
        }
    }
}

@Composable
internal fun ArtworkImage(
    artworkUrl: String?,
    shape: Shape,
    notePainter: Painter,
    placeholderPainter: Painter,
) {
    val context = LocalContext.current
    if (!artworkUrl.isNullOrBlank()) {
        // Сначала пробуем увеличенную обложку, при ошибке — исходную.
        var useOriginal by remember(artworkUrl) { mutableStateOf(false) }
        val targetUrl = if (useOriginal) {
            artworkUrl
        } else {
            com.facet.music.core.ui.artwork.ArtworkUrl.highRes(artworkUrl) ?: artworkUrl
        }
        val imageRequest = remember(targetUrl) {
            ImageRequest.Builder(context)
                .data(targetUrl)
                .size(1024, 1024)
                .crossfade(240)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.player_cover_cd),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = notePainter,
            // maxres/500×500 есть не у всех треков: откатываемся на исходный URL.
            onError = { useOriginal = true },
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

/** Название источника для строки «Источник» под артистом. */
private fun sourceLabel(source: Source): String = when (source) {
    Source.SOUNDCLOUD -> "SoundCloud"
    Source.YOUTUBE -> "YouTube"
    Source.SPOTIFY -> "Spotify"
    Source.LOCAL -> "Local"
    else -> source.name
}
