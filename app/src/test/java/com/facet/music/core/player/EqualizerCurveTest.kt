package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCurveTest {

    @Test
    fun `seven band centers span bass to air`() {
        assertEquals(7, EqualizerCurve.BAND_CENTERS_HZ.size)
        assertEquals(60, EqualizerCurve.BAND_CENTERS_HZ.first())
        assertEquals(12_000, EqualizerCurve.BAND_CENTERS_HZ.last())
        assertTrue(EqualizerCurve.BAND_CENTERS_HZ.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `custom bands follow nearest center frequency`() {
        val bands = listOf(1f, 2f, 3f, 4f, 5f, 6f, -6f)

        assertEquals(1f, EqualizerCurve.customGainDb(bands, 60), 0.001f)
        assertEquals(4f, EqualizerCurve.customGainDb(bands, 1_000), 0.001f)
        assertEquals(-6f, EqualizerCurve.customGainDb(bands, 20_000), 0.001f)
        assertEquals(1f, EqualizerCurve.customGainDb(bands, 20), 0.001f)
    }

    @Test
    fun `empty custom bands are silent`() {
        assertEquals(0f, EqualizerCurve.customGainDb(emptyList(), 440), 0.001f)
    }

    @Test
    fun `preset bands mirror the four zone curve`() {
        val bands = EqualizerCurve.bandsForPreset(EqualizerPreset.BASS)

        assertEquals(6f, bands.first(), 0.001f)
        assertEquals(6f, bands[1], 0.001f)
        assertEquals(2f, bands[2], 0.001f)
        assertEquals(0f, bands[3], 0.001f)
        assertEquals(1f, bands.last(), 0.001f)
    }

    @Test
    fun `flat preset is neutral everywhere`() {
        assertTrue(EqualizerCurve.bandsForPreset(EqualizerPreset.FLAT).all { it == 0f })
    }

    @Test
    fun `millibels are clamped to device range`() {
        assertEquals(600, EqualizerCurve.levelMillibels(6f, -900, 900).toInt())
        assertEquals(900, EqualizerCurve.levelMillibels(30f, -900, 900).toInt())
        assertEquals(-900, EqualizerCurve.levelMillibels(-30f, -900, 900).toInt())
    }
}
