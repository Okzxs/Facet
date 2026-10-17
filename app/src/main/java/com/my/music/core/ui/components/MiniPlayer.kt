package com.my.music.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.data.model.Track
import com.my.music.feature.player.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * Мини-плеер — персистентная панель над BottomNavigationBar.
 * Появление/скрытие анимировано через AnimatedVisibility (slide + fade, ~250 мс).
 */
@Composable
fun MiniPlayer(
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by viewModel.playerState.collectAsState()
    val track = playerState.currentTrack
    val isVisible = track != null

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 250),
        ) + fadeIn(animationSpec = tween(durationMillis = 250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 250),
        ) + fadeOut(animationSpec = tween(durationMillis = 250)),
        modifier = modifier,
    ) {
        if (track != null) {
            MiniPlayerContent(
                track = track,
                isPlaying = playerState.isPlaying,
                onExpandClick = onExpandClick,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                currentFraction = { viewModel.currentFraction() },
            )
        }
    }
}

@Composable
private fun MiniPlayerContent(
    track: Track,
    isPlaying: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    currentFraction: () -> Float,
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column {
            // Тонкий прогресс-бар вверху панели
            MiniPlayerProgressBar(
                isPlaying = isPlaying,
                currentFraction = currentFraction,
            )

            // Основное содержимое
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Обложка с фиксированным контейнером 40.dp
                val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
                val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
                val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
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
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Название и артист
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Кнопка Play/Pause
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                    )
                }
            }
        }
    }
}

/** Изолированный прогресс-бар для минимизации лишних рекомпозиций */
@Composable
private fun MiniPlayerProgressBar(
    isPlaying: Boolean,
    currentFraction: () -> Float,
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        while (true) {
            progress = currentFraction()
            delay(150) // ~6-7 обновлений в секунду
        }
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}
