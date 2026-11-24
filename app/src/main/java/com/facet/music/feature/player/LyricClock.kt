package com.facet.music.feature.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

class LyricClock(
    private val positionProvider: () -> Long,
    private val elapsedProvider: () -> Long,
) {

    private var playing = false
    private var started = false
    private var lastElapsed = 0L
    private var smoothed = 0.0
    private var lastOutput = 0L

    fun reset(positionMs: Long = positionProvider()) {
        smoothed = positionMs.coerceAtLeast(0L).toDouble()
        lastOutput = smoothed.roundToLong()
        lastElapsed = elapsedProvider()
        started = true
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        if (value) {
            reset(positionProvider())
        } else {
            lastElapsed = elapsedProvider()
        }
    }

    fun tick(): Long {
        val now = elapsedProvider()
        val raw = positionProvider().coerceAtLeast(0L)
        if (!started) reset(raw)
        val delta = (now - lastElapsed).coerceAtLeast(0L)
        lastElapsed = now
        val predicted = smoothed + if (playing) delta.toDouble() else 0.0
        val drift = raw - predicted
        val isSeek = abs(drift) > SEEK_THRESHOLD_MS
        var value = if (isSeek) raw.toDouble() else predicted + drift * CORRECTION
        if (!isSeek) value = value.coerceAtLeast(lastOutput.toDouble())
        value = value.coerceAtLeast(0.0)
        smoothed = value
        lastOutput = value.roundToLong()
        return lastOutput
    }

    private companion object {
        const val SEEK_THRESHOLD_MS = 450.0
        const val CORRECTION = 0.18
    }
}

@Composable
fun rememberLyricClock(
    positionProvider: () -> Long,
    isPlaying: Boolean,
    resetKey: Any?,
): Long {
    val currentPosition by rememberUpdatedState(positionProvider)
    val clock = remember { LyricClock({ currentPosition() }, { SystemClock.elapsedRealtime() }) }
    var timeMs by remember { mutableLongStateOf(currentPosition()) }
    LaunchedEffect(resetKey) {
        clock.reset(currentPosition())
        timeMs = clock.tick()
    }
    LaunchedEffect(isPlaying, resetKey) {
        clock.setPlaying(isPlaying)
        if (isPlaying) {
            while (true) {
                withFrameNanos { timeMs = clock.tick() }
            }
        } else {
            while (true) {
                timeMs = clock.tick()
                delay(PAUSED_TICK_MS)
            }
        }
    }
    return timeMs
}

private const val PAUSED_TICK_MS = 120L
