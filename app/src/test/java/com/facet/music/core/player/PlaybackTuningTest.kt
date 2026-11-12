package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTuningTest {

    @Test
    fun `reference frequency keeps pitch unchanged`() {
        assertEquals(1.0f, PlaybackTuning.pitchFactor(440f), 1e-4f)
        assertEquals(0.0, PlaybackTuning.cents(440f), 1e-6)
    }

    @Test
    fun `octave up is twelve hundred cents`() {
        assertEquals(1200.0, PlaybackTuning.cents(880f), 1e-6)
        assertEquals(2.0f, PlaybackTuning.pitchFactor(880f), 1e-4f)
    }

    @Test
    fun `lower tuning lowers pitch`() {
        val pitch432 = PlaybackTuning.pitchFactor(432f)
        assertTrue("432 Гц должен звучать ниже 440", pitch432 < 1.0f)
        assertTrue("сдвиг не должен превышать полутон", pitch432 > 0.96f)
    }

    @Test
    fun `higher tuning raises pitch`() {
        val pitch453 = PlaybackTuning.pitchFactor(453f)
        assertTrue("453 Гц должен звучать выше 440", pitch453 > 1.0f)
        assertTrue("сдвиг не должен превышать полутон", pitch453 < 1.04f)
    }

    @Test
    fun `all available frequencies are within one semitone`() {
        PlaybackTuning.FREQUENCIES.forEach { hz ->
            val cents = PlaybackTuning.cents(hz.toFloat())
            assertTrue("$hz Гц вне диапазона ±100 центов", kotlin.math.abs(cents) <= 100.0)
        }
    }

    @Test
    fun `variations keep the promised speed and pitch`() {
        assertEquals(1.0f, PlaybackVariation.NORMAL.speed)
        assertEquals(1.25f, PlaybackVariation.SPEED_UP.speed)
        assertEquals(1.2f, PlaybackVariation.SPEED_UP.pitch)
        assertEquals(0.8f, PlaybackVariation.SLOWED.speed)
        assertEquals(1.3f, PlaybackVariation.NIGHTCORE.speed)
        assertEquals(1.3f, PlaybackVariation.NIGHTCORE.pitch)
    }
}
