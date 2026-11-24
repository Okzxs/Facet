package com.facet.music.core.player

import kotlin.math.abs
import kotlin.math.min

class PeakLimiterState {
    var gain: Float = 1f
}

object PeakLimiter {

    const val CEILING = 0.95f

    fun process(samples: FloatArray, state: PeakLimiterState) {
        var peak = 0f
        for (sample in samples) {
            val value = abs(sample)
            if (value > peak) peak = value
        }
        if (peak <= 0f) return
        val target = if (peak > CEILING) CEILING / peak else 1f
        val nextGain = if (target < state.gain) {
            target
        } else {
            state.gain + (target - state.gain) * RELEASE
        }
        state.gain = nextGain.coerceIn(MIN_GAIN, 1f)
        if (state.gain >= 1f) return
        for (index in samples.indices) {
            samples[index] = (samples[index] * state.gain).coerceIn(-1f, 1f)
        }
    }

    fun gainForPeak(peak: Float, previousGain: Float): Float {
        val target = if (peak > CEILING) CEILING / peak else 1f
        val next = if (target < previousGain) {
            target
        } else {
            previousGain + (target - previousGain) * RELEASE
        }
        return min(next, 1f).coerceAtLeast(MIN_GAIN)
    }

    private const val RELEASE = 0.002f

    private const val MIN_GAIN = 0.2f
}
