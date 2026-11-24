package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCurveTest {

    @Test
    fun `flat preset keeps every band at zero`() {
        listOf(60, 230, 910, 3_600, 14_000).forEach { hz ->
            assertEquals(0f, EqualizerCurve.gainDb(EqualizerPreset.FLAT, hz), 0.001f)
        }
    }

    @Test
    fun `bass preset lifts lows more than highs`() {
        val low = EqualizerCurve.gainDb(EqualizerPreset.BASS, 60)
        val high = EqualizerCurve.gainDb(EqualizerPreset.BASS, 14_000)

        assertTrue(low > high)
        assertTrue(low > 0f)
    }

    @Test
    fun `vocal preset lifts upper mids`() {
        assertTrue(EqualizerCurve.gainDb(EqualizerPreset.VOCAL, 2_500) > 0f)
        assertTrue(EqualizerCurve.gainDb(EqualizerPreset.VOCAL, 60) < 0f)
    }

    @Test
    fun `millibel conversion clamps to device range`() {
        assertEquals(600.toShort(), EqualizerCurve.levelMillibels(6f, -1_500, 1_500))
        assertEquals(1_500.toShort(), EqualizerCurve.levelMillibels(15f, -1_500, 1_500))
        assertEquals(1_500.toShort(), EqualizerCurve.levelMillibels(30f, -1_500, 1_500))
        assertEquals((-1_500).toShort(), EqualizerCurve.levelMillibels(-30f, -1_500, 1_500))
    }

    @Test
    fun `unknown preset name falls back to flat`() {
        assertEquals(EqualizerPreset.FLAT, EqualizerPreset.fromName("unknown"))
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromName("rock"))
    }
}
