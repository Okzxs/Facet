package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakLimiterTest {

    @Test
    fun `loud block is limited below the ceiling immediately`() {
        val state = PeakLimiterState()
        val samples = floatArrayOf(1.6f, -1.4f, 0.8f, -0.2f)

        PeakLimiter.process(samples, state)

        val peak = samples.maxOf { kotlin.math.abs(it) }
        assertTrue(peak <= PeakLimiter.CEILING + 0.001f)
        assertTrue(state.gain < 1f)
    }

    @Test
    fun `quiet block passes untouched`() {
        val state = PeakLimiterState()
        val samples = floatArrayOf(0.1f, -0.2f, 0.15f)

        PeakLimiter.process(samples, state)

        assertEquals(1f, state.gain, 0.0001f)
        assertEquals(0.1f, samples[0], 0.0001f)
        assertEquals(-0.2f, samples[1], 0.0001f)
    }

    @Test
    fun `gain recovers slowly after a loud block`() {
        val state = PeakLimiterState()
        PeakLimiter.process(floatArrayOf(1.5f), state)
        val limited = state.gain

        PeakLimiter.process(floatArrayOf(0.2f), state)

        assertTrue(state.gain > limited)
        assertTrue(state.gain < 1f)
    }

    @Test
    fun `gain never drops below the floor`() {
        val state = PeakLimiterState()
        PeakLimiter.process(floatArrayOf(100f), state)

        assertTrue(state.gain >= 0.2f)
    }
}
