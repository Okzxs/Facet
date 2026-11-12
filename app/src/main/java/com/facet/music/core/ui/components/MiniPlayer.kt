package com.facet.music.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.data.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Высота островка мини-плеера без внешних отступов. */
internal val MiniPlayerIslandHeight = 64.dp

/** Боковые отступы островка от краёв экрана. */
internal val MiniPlayerHorizontalMargin = 16.dp

/** Зазор между островком и верхней кромкой навигационного бара. */
internal val MiniPlayerGap = 12.dp

/** Доля ширины островка, после которой свайп переключает трек. */
private const val SwipeThresholdFraction = 0.3f

/** Скорость свайпа (px/s), при которой трек переключается без набора дистанции. */
private const val SwipeVelocityThreshold = 1000f

/**
 * Направление переключения по свайпу: +1 — следующий, -1 — предыдущий,
 * 0 — недостаточно дистанции и скорости, контент возвращается на место.
 *
 * Порог дистанции — 30% ширины островка, альтернативно — скорость больше
 * 1000 px/s в сторону свайпа. Функция чистая, чтобы пороги тестировались
 * отдельно от Compose-жеста.
 */
internal fun swipeTrackDirection(offsetPx: Float, widthPx: Float, velocityX: Float): Int {
    if (widthPx <= 0f) return 0
    val threshold = widthPx * SwipeThresholdFraction
    return when {
        offsetPx <= -threshold || velocityX < -SwipeVelocityThreshold -> 1
        offsetPx >= threshold || velocityX > SwipeVelocityThreshold -> -1
        else -> 0
    }
}

/**
 * Плавающий мини-плеер: отдельный островок над навигационным баром.
 *
 * Высота 64dp, обложка 48dp, скругление 30dp и тень 8dp. Внизу — полоска
 * прогресса 2dp с отступом 24dp, чтобы она не выходила за скругление.
 *
 * Горизонтальный свайп переключает треки как в SoundCloud: влево — следующий
 * ([onNext]), вправо — предыдущий ([onPrevious]). Порог — 30% ширины островка
 * или скорость больше 1000 px/s. Обложка и текст следуют за пальцем, после
 * отпускания пружинно возвращаются на место.
 *
 * Короткий тап по островку раскрывает полноэкранный плеер.
 */
@Composable
internal fun FloatingMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCloseClick: () -> Unit,
    currentFraction: () -> Float,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shape = MaterialTheme.shapes.extraLarge
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    val latestFraction = rememberUpdatedState(currentFraction)
    val latestNext = rememberUpdatedState(onNext)
    val latestPrevious = rememberUpdatedState(onPrevious)

    val stateText = stringResource(
        when {
            isBuffering -> R.string.mini_player_state_loading
            isPlaying -> R.string.mini_player_state_playing
            else -> R.string.mini_player_state_paused
        },
    )
    val nextLabel = stringResource(R.string.common_next_cd)
    val previousLabel = stringResource(R.string.common_previous_cd)

    // Смещение контента при свайпе живёт в draw-state: graphicsLayer читает его
    // без рекомпозиции. DraggableState применяет дельты синхронно, без запуска
    // корутины на каждое событие; пружинный возврат — одна корутина на жест.
    val offsetState = remember { mutableFloatStateOf(0f) }
    val widthState = remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val draggableState = remember {
        DraggableState { delta ->
            val max = widthState.floatValue.coerceAtLeast(1f)
            offsetState.floatValue = (offsetState.floatValue + delta).coerceIn(-max, max)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MiniPlayerIslandHeight)
            .shadow(6.dp, shape)
            .background(surfaceColor, shape)
            .clip(shape)
            .onSizeChanged { widthState.floatValue = it.width.toFloat() }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    val direction = swipeTrackDirection(
                        offsetPx = offsetState.floatValue,
                        widthPx = widthState.floatValue,
                        velocityX = velocity,
                    )
                    if (direction != 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (direction > 0) latestNext.value() else latestPrevious.value()
                    }
                    settleJob = scope.launch {
                        settle.snapTo(offsetState.floatValue)
                        settle.animateTo(0f, Motion.spatialDefault()) {
                            offsetState.floatValue = value
                        }
                    }
                },
            )
            .clickable(role = Role.Button, onClick = onExpandClick)
            .semantics {
                stateDescription = stateText
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { latestNext.value(); true },
                    CustomAccessibilityAction(previousLabel) { latestPrevious.value(); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Обложка и подписи едут за пальцем; кнопка Play остаётся на месте.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = offsetState.floatValue
                        val travel = (abs(offsetState.floatValue) / size.width.toFloat().coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        alpha = 1f - travel * 0.25f
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .then(coverModifier),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        val imageRequest = remember(track.artworkUrl) {
                            ImageRequest.Builder(context)
                                .data(track.artworkUrl)
                                .crossfade(200)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = placeholderPainter,
                            error = notePainter,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small),
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
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            letterSpacing = 0.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Визуально 40dp, зона касания 48dp. Во время буферизации — индикатор
            // загрузки, повторные нажатия игнорируются.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        enabled = !isBuffering,
                        onClick = onPlayPauseClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (isPlaying) {
                                    stringResource(R.string.common_pause_cd)
                                } else {
                                    stringResource(R.string.common_play_cd)
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(2.dp))

            // Крестик закрывает плеер целиком: стоп, пустая очередь, скрытие
            // островка. Зона касания 48dp, иконка 20dp.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onCloseClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.player_close_mini),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Тонкий прогресс внизу островка: inset 24dp держит его внутри скругления.
        MiniPlayerProgressBar(
            fraction = { latestFraction.value() },
            isPlaying = isPlaying,
            resetKey = track.id,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 3.dp)
                .fillMaxWidth(),
        )
    }
}

/**
 * Полоска прогресса: значение читается в drawBehind, кадровый тик инвалидирует
 * только отрисовку (не рекомпозицию). На паузе цикл не крутится — лишних кадров нет.
 */
@Composable
private fun MiniPlayerProgressBar(
    fraction: () -> Float,
    isPlaying: Boolean,
    resetKey: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val latestFraction = rememberUpdatedState(fraction)
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(resetKey, isPlaying) {
        progress.floatValue = latestFraction.value().coerceIn(0f, 1f)
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                progress.floatValue = latestFraction.value().coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .height(2.dp)
            .clip(PillShape)
            .drawBehind {
                val f = progress.floatValue
                drawRect(trackColor)
                drawRect(primary, size = Size(size.width * f, size.height))
            },
    )
}
