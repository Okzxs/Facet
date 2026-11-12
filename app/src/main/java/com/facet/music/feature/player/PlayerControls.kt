package com.facet.music.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.facet.music.core.ui.components.ExpressivePlayerSlider
import com.facet.music.core.ui.components.PlayerSliderState
import com.facet.music.core.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerProgressSection(
    isPlaying: Boolean,
    playbackClock: () -> Long,
    positionMs: () -> Long,
    trackId: String? = null,
    durationMs: Long,
    primaryColor: Color,
    isBuffering: Boolean = false,
    isResolving: Boolean = false,
    bufferedFraction: () -> Float = { 0f },
    onSeek: (Float) -> Unit,
) {
    var sliderState by remember(trackId) { mutableStateOf(PlayerSliderState()) }
    var playbackFraction by remember(trackId) { mutableFloatStateOf(0f) }
    var playbackPositionMs by remember(trackId) { mutableLongStateOf(0L) }
    var bufferFrac by remember(trackId) { mutableFloatStateOf(0f) }
    val latestTrackId by rememberUpdatedState(trackId)
    val latestDuration by rememberUpdatedState(durationMs)
    val latestClock by rememberUpdatedState(playbackClock)
    val latestRawPosition by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestBuffer by rememberUpdatedState(bufferedFraction)
    val latestSeek by rememberUpdatedState(onSeek)

    fun emit() {
        val position = if (latestPlaying) latestClock() else latestRawPosition()
        playbackPositionMs = position
        val duration = latestDuration
        playbackFraction = if (duration > 0) position.toFloat() / duration else 0f
        bufferFrac = latestBuffer()
    }

    LaunchedEffect(trackId, isPlaying) {
        if (!isPlaying) {
            while (true) {
                emit()
                delay(PROGRESS_PAUSED_POLL_MS)
            }
        }
        var lastEmit = 0L
        while (true) {
            withFrameMillis { frame ->
                if (frame - lastEmit >= PROGRESS_EMIT_INTERVAL_MS) {
                    lastEmit = frame
                    emit()
                }
            }
        }
    }

    val displayPosition = sliderState.display(playbackFraction)
    val displayedMs = sliderState.preview?.let { (it * durationMs).toLong() } ?: playbackPositionMs

    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0L)
    val isInitialBuffering = (isBuffering || isResolving) && displayedMs == 0L
    var hasStarted by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(trackId, isInitialBuffering) {
        if (!isInitialBuffering) hasStarted = true
    }
    val showLoading = isInitialBuffering && !hasStarted

    Column(modifier = Modifier.fillMaxWidth()) {
        Crossfade(
            targetState = showLoading,
            animationSpec = tween(
                durationMillis = Motion.EnterMillis,
                easing = Motion.EmphasizedDecelerate,
            ),
            label = "progressCrossfade",
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                        emit()
                                        sliderState.cancel()
                                    }
                                    else -> sliderState
                                }
                            }
                        },
                        onValueChangeFinished = {
                            if (latestTrackId == gestureTrack) {
                                sliderState.preview?.let { latestSeek(it) }
                                sliderState = sliderState.cancel()
                                emit()
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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

internal fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

internal const val PROGRESS_EMIT_INTERVAL_MS = 100L

internal const val PROGRESS_PAUSED_POLL_MS = 250L
