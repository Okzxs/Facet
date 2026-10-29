package com.my.music.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.data.model.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Переиспользуемая карточка трека — горизонтальный layout.
 * Используется в поиске, главной, библиотеке. В покое фон прозрачный:
 * список держится на воздухе и отступах, а не на плашках.
 */
@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    isPlaying: Boolean = false,
    isPaused: Boolean = false,
    artworkSize: Int = 56,
    artworkCornerDp: Int = 12,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = MaterialTheme.shapes.medium

    val containerColor by animateColorAsState(
        targetValue = when {
            pressed -> MaterialTheme.colorScheme.surfaceContainerHigh
            // Играющий трек подсвечен в списке: тон чуть выше обычной карточки.
            isPlaying -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 180),
        label = "trackCardContainer",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "trackCardScale",
    )

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            },
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        ListItem(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            ),
            headlineContent = {
                // Название и автор живут в одной колонке без supporting-слота:
                // ListItem иначе разводит их по высоте, и у коротких названий
                // между строками появляется пустая дыра.
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(track.artist)
                            if (!track.genre.isNullOrBlank()) append(" · ${track.genre}")
                            if (track.durationMs > 0) append(" · ${formatDuration(track.durationMs)}")
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Box {
                    TrackArtwork(
                        url = track.artworkUrl,
                        contentDescription = "${track.title} обложка",
                        size = artworkSize,
                        cornerDp = artworkCornerDp,
                    )
                    // Значок «скачано» — маленькая галочка поверх обложки
                    if (isDownloaded) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = "Трек скачан",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }
                }
            },
            trailingContent = if (isPlaying || trailingContent != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPlaying) {
                            if (isPaused) {
                                PausedEqualizerDots(
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            } else {
                                PlayingEqualizerIcon(
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        }
                        trailingContent?.invoke()
                    }
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * Эквалайзер из трёх полосок для играющего трека: высоты бегут по кругу
 * через infiniteTransition, отрисовка на Canvas без лишних рекомпозиций.
 */
@Composable
fun PlayingEqualizerIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "equalizerPhase",
    )
    Canvas(modifier.size(16.dp)) {
        val barWidth = size.width / 5f
        val step = barWidth * 2f
        for (index in 0..2) {
            val t = (phase + index * 0.33f) % 1f
            // sin/PI работают в Double — явно сужаем к Float для геометрии Canvas.
            val wave = abs(sin(t * 2.0 * PI)).toFloat()
            val barHeight = size.height * (0.35f + 0.65f * wave)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * step, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Три статичные точки вместо бегущих полосок: трек на паузе.
 * Тот же размер, что у эквалайзера, чтобы строка не «дёргалась».
 */
@Composable
fun PausedEqualizerDots(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(16.dp)) {
        val radius = size.minDimension * 0.14f
        val y = size.height / 2f
        for (index in 0..2) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(
                    x = size.width * (index * 2f + 1f) / 6f,
                    y = y,
                ),
            )
        }
    }
}

@Composable
fun TrackArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 56,
    cornerDp: Int = 12,
) {
    val shape = RoundedCornerShape(cornerDp.dp)
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    Surface(
        modifier = modifier.size(size.dp),
        shape = shape,
        color = surfaceColor,
    ) {
        if (!url.isNullOrBlank()) {
            val imageRequest = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(200)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
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
                    modifier = Modifier.size((size / 2).dp),
                )
            }
        }
    }
}

/** Форматирование длительности: мс → "3:22" */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
