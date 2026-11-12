package com.facet.music.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

private const val TWO_PI_F = 6.2831855f

@Composable
internal fun rememberPlaybackPhase(active: Boolean, periodMs: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, periodMs) {
        if (!active) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dtMs = (now - lastNanos) / 1_000_000f
                    phase.floatValue = (phase.floatValue + dtMs * TWO_PI_F / periodMs) % TWO_PI_F
                }
                lastNanos = now
            }
        }
    }
    return phase
}
