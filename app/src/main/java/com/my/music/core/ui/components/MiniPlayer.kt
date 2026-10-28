package com.my.music.core.ui.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

/** Высота островка мини-плеера без внешних отступов. */
internal val MiniPlayerIslandHeight = 64.dp

/** Боковые отступы островка от краёв экрана. */
internal val MiniPlayerHorizontalMargin = 16.dp

/** Зазор между островком и верхней кромкой навигационного бара. */
internal val MiniPlayerGap = 12.dp

/**
 * Плавающий мини-плеер: отдельный островок над навигационным баром.
 *
 * Высота 64dp, обложка 48dp, скругление 30dp и тень 8dp. Внизу — полоска
 * прогресса 2dp с отступом 24dp, чтобы она не выходила за скругление.
 *
 * Горизонтальный свайп по островку перематывает трек: полоска следует
 * за пальцем, а [onSeekToFraction] вызывается только при отпускании.
 * Короткий тап по островку раскрывает полноэкранный плеер.
 */
@Composable
internal fun FloatingMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    currentFraction: () -> Float,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(30.dp)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    // Колбэки могут пересоздаваться на каждой рекомпозиции, а жест и опрос
    // позиции живут дольше — читаем актуальные версии через rememberUpdatedState.
    val latestFraction = rememberUpdatedState(currentFraction)
    val latestSeek = rememberUpdatedState(onSeekToFraction)

    var progress by remember(track.id) { mutableFloatStateOf(0f) }
    var isSeeking by remember(track.id) { mutableStateOf(false) }
    var seekStartFraction by remember(track.id) { mutableFloatStateOf(0f) }

    // Пока палец на островке, полоска живёт по жесту и не дёргается от опроса.
    // После перемотки ждём 120 мс: плееру нужно мгновение, чтобы обновить
    // позицию, иначе полоска на кадр прыгнет назад к старому значению.
    LaunchedEffect(track.id, isPlaying, isSeeking) {
        if (isSeeking) return@LaunchedEffect
        delay(120)
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
                        seekStartFraction = progress
                        isSeeking = true
                    },
                    onDragEnd = {
                        isSeeking = false
                        latestSeek.value(progress)
                    },
                    onDragCancel = {
                        // Жест отменён — возвращаем полоску к позиции плеера.
                        isSeeking = false
                        progress = seekStartFraction
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (size.width > 0) {
                        val delta = dragAmount / size.width
                        progress = (progress + delta).coerceIn(0f, 1f)
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
            Surface(
                modifier = Modifier.size(48.dp),
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

/** Полоска прогресса, управляемая снаружи — во время свайпа рисует позицию пальца. */
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
