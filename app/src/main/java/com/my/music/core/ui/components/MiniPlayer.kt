package com.my.music.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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

/**
 * Строка мини-плеера внутри общего нижнего дока.
 * Высота 56dp: обложка 44dp, название 14sp SemiBold, артист 11sp, Play 40dp.
 * Собственной панели и тени нет — фон и форма принадлежат доку.
 */
@Composable
internal fun MiniPlayerRow(
    track: Track,
    isPlaying: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    currentFraction: () -> Float,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClick = onExpandClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = surfaceColor,
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
                            modifier = Modifier.size(20.dp),
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

        // Тонкий прогресс вверху, внутри клипа дока.
        MiniPlayerProgressBar(
            isPlaying = isPlaying,
            currentFraction = currentFraction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // Разделитель с рядом навигации, inset 16dp.
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        )
    }
}

/** Изолированный прогресс-бар для минимизации лишних рекомпозиций. */
@Composable
private fun MiniPlayerProgressBar(
    isPlaying: Boolean,
    currentFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        while (true) {
            progress = currentFraction()
            delay(150) // ~6-7 обновлений в секунду
        }
    }

    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(2.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
