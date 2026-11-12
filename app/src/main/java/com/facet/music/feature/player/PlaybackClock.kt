package com.facet.music.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import kotlin.math.abs
import kotlin.math.roundToLong

internal class PlaybackClock {
    internal var baseMs = 0L
    internal var baseFrameMs = 0L
    internal var speed = 1f
    internal var playing = false
    internal var frameMs = 0L

    fun nowMs(): Long =
        if (playing) {
            baseMs + ((frameMs - baseFrameMs) * speed).roundToLong().coerceAtLeast(0L)
        } else {
            baseMs
        }

    internal fun resync(positionMs: Long, frameMs: Long) {
        baseMs = positionMs
        baseFrameMs = frameMs
        this.frameMs = frameMs
    }
}

@Composable
internal fun rememberPlaybackClock(
    positionMs: () -> Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
): () -> Long {
    val clock = remember { PlaybackClock() }
    val latestPosition by rememberUpdatedState(positionMs)

    LaunchedEffect(isPlaying, playbackSpeed) {
        clock.speed = playbackSpeed.coerceAtLeast(0.05f)
        if (isPlaying) {
            clock.playing = true
            try {
                withFrameMillis { frame -> clock.resync(latestPosition(), frame) }
                while (true) {
                    withFrameMillis { frame ->
                        val expected = clock.nowMs()
                        val actual = latestPosition()
                        if (abs(actual - expected) > RESYNC_DRIFT_MS) {
                            clock.resync(actual, frame)
                        } else {
                            clock.frameMs = frame
                        }
                    }
                }
            } finally {
                clock.playing = false
                clock.baseMs = latestPosition()
            }
        } else {
            clock.playing = false
            clock.baseMs = latestPosition()
        }
    }
    return { clock.nowMs() }
}

internal const val RESYNC_DRIFT_MS = 300L
