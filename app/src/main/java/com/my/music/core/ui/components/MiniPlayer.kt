package com.my.music.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.ui.theme.PillShape
import com.my.music.data.model.Track
import kotlinx.coroutines.delay
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
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    currentFraction: () -> Float,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(30.dp)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    // Колбэки могут пересоздаваться на каждой рекомпозиции, а жест и опрос
    // позиции живут дольше — читаем актуальные версии через rememberUpdatedState.
    val latestFraction = rememberUpdatedState(currentFraction)
    val latestNext = rememberUpdatedState(onNext)
    val latestPrevious = rememberUpdatedState(onPrevious)

    var progress by remember(track.id) { mutableFloatStateOf(0f) }

    // Смещение контента при свайпе: ноль в покое, растёт за пальцем,
    // после отпускания возвращается пружиной.
    val offsetX = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }

    LaunchedEffect(track.id, isPlaying) {
        progress = latestFraction.value().coerceIn(0f, 1f)
        while (isPlaying) {
            delay(150) // ~6-7 обновлений в секунду
            progress = latestFraction.value().coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerIslandHeight)
            // Тень рисуется до клипа, поэтому не обрезается по контуру островка.
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .background(surfaceColor, shape)
            .clip(shape)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        // Новый жест прерывает пружину возврата.
                        scope.launch { offsetX.stop() }
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocityX = velocityTracker.calculateVelocity().x
                        val direction = swipeTrackDirection(
                            offsetPx = offsetX.value,
                            widthPx = size.width.toFloat(),
                            velocityX = velocityX,
                        )

                        if (direction != 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (direction > 0) latestNext.value() else latestPrevious.value()
                        }

                        // Новый трек уже подставлен — контент пружинно встаёт на место.
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch {
                        val maxOffset = size.width.toFloat()
                        offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-maxOffset, maxOffset))
                    }
                }
            }
            .clickable(role = Role.Button, onClick = onExpandClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Обложка и подписи едут за пальцем; кнопка Play остаётся на месте.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = offsetX.value
                        val travel = (abs(offsetX.value) / size.width.toFloat().coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        alpha = 1f - travel * 0.25f
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .then(coverModifier),
                    shape = RoundedCornerShape(14.dp),
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
                                .clip(RoundedCornerShape(14.dp)),
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

                Spacer(Modifier.width(12.dp))

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

            // Визуально 40dp, зона касания 48dp.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Пауза" else "Играть",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        // Тонкий прогресс внизу островка: inset 24dp держит его внутри скругления.
        MiniPlayerProgressBar(
            fraction = progress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 3.dp)
                .fillMaxWidth(),
        )
    }
}

/** Полоска прогресса, которую обновляет опрос позиции плеера. */
@Composable
private fun MiniPlayerProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(2.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
