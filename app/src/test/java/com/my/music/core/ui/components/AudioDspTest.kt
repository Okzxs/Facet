package com.my.music.core.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class AudioDspTest {
    private fun fft(size: Int = 1024, vararg bins: Pair<Int, Pair<Int, Int>>) = ByteArray(size).apply {
        bins.forEach { (bin, complex) -> this[2 * bin] = complex.first.toByte(); this[2 * bin + 1] = complex.second.toByte() }
    }

    @Test fun bandEnergiesSplitSpectrum() {
        val kick = bandEnergies(fft(1024, 1 to (64 to 0)), 44_100_000)
        assertTrue(kick[0] > 0f)
        assertEquals(0f, kick[1], 0f)
        assertEquals(0f, kick[2], 0f)

        val snare = bandEnergies(fft(1024, 4 to (64 to 0)), 44_100_000)
        assertEquals(0f, snare[0], 0f)
        assertTrue(snare[1] > 0f)
        assertEquals(0f, snare[2], 0f)

        val presence = bandEnergies(fft(1024, 35 to (64 to 0)), 44_100_000)
        assertEquals(0f, presence[0], 0f)
        assertEquals(0f, presence[1], 0f)
        assertTrue(presence[2] > 0f)
    }
    @Test fun bandsIgnoreGapsAndOutOfRange() {
        // 20 Гц, ~129 Гц, ~861 Гц и ~6029 Гц не попадают ни в одну полосу
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 2 to (64 to 0)), 10_240_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 3 to (64 to 0)), 44_100_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 20 to (64 to 0)), 44_100_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 140 to (64 to 0)), 44_100_000), 0f)
    }
    @Test fun bandBoundariesAreInclusive() {
        // Kick: 30 и 120 Гц входят, 20 и 130 Гц — нет
        assertTrue(bandEnergies(fft(1024, 3 to (64 to 0)), 10_240_000)[0] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 2 to (64 to 0)), 10_240_000), 0f)
        assertTrue(bandEnergies(fft(1024, 12 to (64 to 0)), 10_240_000)[0] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 13 to (64 to 0)), 10_240_000), 0f)
        // Snare_Vocal: 150 и 600 Гц входят, 140 и 610 Гц — нет
        assertTrue(bandEnergies(fft(1024, 15 to (64 to 0)), 10_240_000)[1] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 14 to (64 to 0)), 10_240_000), 0f)
        assertTrue(bandEnergies(fft(1024, 60 to (64 to 0)), 10_240_000)[1] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 61 to (64 to 0)), 10_240_000), 0f)
        // Presence: ~1507 и ~5986 Гц входят, ~1464 и ~6029 Гц — нет
        assertTrue(bandEnergies(fft(1024, 35 to (64 to 0)), 44_100_000)[2] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 34 to (64 to 0)), 44_100_000), 0f)
        assertTrue(bandEnergies(fft(1024, 139 to (64 to 0)), 44_100_000)[2] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 140 to (64 to 0)), 44_100_000), 0f)
    }
    @Test fun bandEnergyUsesMeanPower() {
        // Два бина Kick по 64: средний RMS = 64, после нормировки 0.5
        val kick = bandEnergies(fft(1024, 1 to (64 to 0), 2 to (64 to 0)), 44_100_000)
        assertEquals(0.5f, kick[0], 0.00001f)
    }
    @Test fun spectralFluxCountsOnlyRises() {
        val previous = floatArrayOf(0.2f, 0.4f, 0.3f)
        val current = floatArrayOf(0.5f, 0.1f, 0.3f)
        assertEquals(0.36f, spectralFlux(current, previous, floatArrayOf(0f, 0f, 0f)), 0.00001f)
    }
    @Test fun spectralFluxSumsWeightedBands() {
        val previous = floatArrayOf(0f, 0f, 0f)
        val current = floatArrayOf(0.2f, 0.1f, 0.05f)
        // 0.2 * 1.2 + 0.1 * 1.5 + 0.05 * 1.0
        assertEquals(0.44f, spectralFlux(current, previous, floatArrayOf(0f, 0f, 0f)), 0.00001f)
        assertEquals(1f, spectralFlux(floatArrayOf(1f, 1f, 1f), previous, floatArrayOf(0f, 0f, 0f)), 0f)
    }
    @Test fun spectralFluxIgnoresBelowThreshold() {
        val previous = floatArrayOf(0f, 0f, 0f)
        assertEquals(
            0f,
            spectralFlux(floatArrayOf(0.1f, 0.1f, 0.1f), previous, floatArrayOf(0.12f, 0.12f, 0.12f)),
            0f,
        )
        assertEquals(
            0.24f,
            spectralFlux(floatArrayOf(0.2f, 0.1f, 0.1f), previous, floatArrayOf(0.15f, 0.12f, 0.12f)),
            0.00001f,
        )
    }
    @Test fun detectorFirstFrameIsBaseline() {
        val detector = TransientDetector()
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0))
        assertEquals(0f, detector.process(kick, 44_100_000), 0f)
        assertEquals(0f, detector.process(kick, 44_100_000), 0.00001f)
        detector.reset()
        assertEquals(0f, detector.process(kick, 44_100_000), 0f)
    }
    @Test fun detectorIgnoresWeakNoise() {
        val detector = TransientDetector()
        val quiet = fft(1024, 1 to (3 to 0), 2 to (3 to 0))
        val slightlyLouder = fft(1024, 1 to (4 to 0), 2 to (4 to 0))
        detector.process(quiet, 44_100_000)
        repeat(3) { assertEquals(0f, detector.process(slightlyLouder, 44_100_000), 0.00001f) }
        // Сильный удар всё равно проходит порог
        assertTrue(detector.process(fft(1024, 1 to (64 to 0), 2 to (64 to 0)), 44_100_000) > 0.4f)
    }
    @Test fun detectorAdaptiveThresholdRejectsWeakRise() {
        val detector = TransientDetector()
        val base = fft(1024, 1 to (13 to 0), 2 to (13 to 0))
        val weakRise = fft(1024, 1 to (17 to 0), 2 to (17 to 0))
        detector.process(base, 44_100_000)
        // 0.133 ниже 1.5 * 0.102 — это ещё не удар
        assertEquals(0f, detector.process(weakRise, 44_100_000), 0.00001f)
        // 0.5 уверенно превышает адаптивный порог
        assertTrue(detector.process(fft(1024, 1 to (64 to 0), 2 to (64 to 0)), 44_100_000) > 0.4f)
    }
    @Test fun detectorHitThenSilence() {
        val detector = TransientDetector()
        val quiet = ByteArray(1024)
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0))
        detector.process(quiet, 44_100_000)
        val hit = detector.process(kick, 44_100_000)
        assertTrue(hit > 0.4f)
        assertEquals(0f, detector.process(kick, 44_100_000), 0.00001f)
        assertEquals(0f, detector.process(quiet, 44_100_000), 0.00001f)
    }
    @Test fun transientSpikeThenFastDecay() {
        val detector = TransientDetector()
        val quiet = ByteArray(1024)
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0))
        detector.process(quiet, 44_100_000)
        val hit = detector.process(kick, 44_100_000)
        assertTrue(hit > 0.4f)

        val envelope = AudioEnvelope()
        envelope.update(0f, 0)
        val peak = envelope.update(hit, 8_000_000)
        assertTrue(peak > hit * 0.6f)
        val decayed = envelope.update(0f, 208_000_000)
        assertTrue(decayed < peak * 0.4f)
        assertTrue(decayed > 0f)
    }
    @Test fun attackUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0)
        assertEquals((1 - exp(-1.0)).toFloat(), envelope.update(1f, 8_000_000), 0.00001f)
    }
    @Test fun releaseUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0); val peak = envelope.update(1f, 150_000_000)
        assertEquals(peak * exp(-1.0).toFloat(), envelope.update(0f, 350_000_000), 0.00001f)
    }
    @Test fun updateCadenceDoesNotChangeEnvelope() {
        val once = AudioEnvelope(); once.update(1f, 0)
        val many = AudioEnvelope(); many.update(1f, 0)
        for (i in 1..100) many.update(1f, i * 1_000_000L)
        assertEquals(once.update(1f, 100_000_000), many.value, 0.00001f)
    }
    @Test fun nonMonotonicClockDoesNotMoveEnvelope() {
        val envelope = AudioEnvelope(); envelope.update(1f, 100); val value = envelope.update(1f, 8_000_100)
        assertEquals(value, envelope.update(0f, 50), 0f)
        assertEquals(value, envelope.update(0f, 8_000_100), 0f)
    }
    @Test fun staleCallbacksDecayToZero() {
        val envelope = AudioEnvelope(); envelope.update(0f, 0)
        val sample = AudioSample(1f, 0)
        assertTrue(envelope.update(sample.freshValue(100_000_000), 100_000_000) > 0.9f)
        assertEquals(0f, sample.freshValue(120_000_001), 0f)
        assertTrue(envelope.update(sample.freshValue(3_000_000_000), 3_000_000_000) < 0.00001f)
    }
    @Test fun missingCallbacksAndFutureTimestampAreZero() {
        assertEquals(0f, AudioSample().freshValue(0), 0f)
        assertEquals(0f, AudioSample(1f, 100).freshValue(50), 0f)
    }
    @Test fun nonFiniteTargetDoesNotPoisonEnvelope() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0); envelope.update(1f, 8_000_000)
        assertTrue(envelope.update(Float.NaN, 16_000_000).isFinite())
    }
    @Test fun invalidInputsReturnZero() {
        listOf(ByteArray(0), ByteArray(2), ByteArray(7)).forEach {
            assertArrayEquals(FloatArray(3), bandEnergies(it, 44_100_000), 0f)
        }
        assertArrayEquals(FloatArray(3), bandEnergies(ByteArray(1024), 0), 0f)
        assertEquals(0f, TransientDetector().process(ByteArray(7), 44_100_000), 0f)
    }
    @Test fun bandEnergySaturates() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), bandEnergies(ByteArray(1024) { 127 }, 44_100_000), 0f)
    }
}
