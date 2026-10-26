package com.my.music.core.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class AudioDspTest {
    private fun fft(size: Int = 1024, vararg bins: Pair<Int, Pair<Int, Int>>) = ByteArray(size).apply {
        bins.forEach { (bin, complex) -> this[2 * bin] = complex.first.toByte(); this[2 * bin + 1] = complex.second.toByte() }
    }

    @Test fun bandEnergiesSplitSpectrum() {
        val low = bandEnergies(fft(1024, 2 to (64 to 0)), 44_100_000)
        assertTrue(low[0] > 0f)
        assertEquals(0f, low[1], 0f)
        assertEquals(0f, low[2], 0f)

        val mid = bandEnergies(fft(1024, 4 to (64 to 0)), 44_100_000)
        assertEquals(0f, mid[0], 0f)
        assertTrue(mid[1] > 0f)
        assertEquals(0f, mid[2], 0f)

        val high = bandEnergies(fft(1024, 70 to (64 to 0)), 44_100_000)
        assertEquals(0f, high[0], 0f)
        assertEquals(0f, high[1], 0f)
        assertTrue(high[2] > 0f)
    }
    @Test fun bandsIgnoreGapsAndOutOfRange() {
        // 30 Гц, ~1507 Гц, ~2153 Гц и ~10034 Гц не попадают ни в одну полосу
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 3 to (64 to 0)), 10_240_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 35 to (64 to 0)), 44_100_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 50 to (64 to 0)), 44_100_000), 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 233 to (64 to 0)), 44_100_000), 0f)
    }
    @Test fun bandBoundariesAreInclusive() {
        // 40 Гц входит в Low, 30 Гц — нет
        assertTrue(bandEnergies(fft(1024, 4 to (64 to 0)), 10_240_000)[0] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 3 to (64 to 0)), 10_240_000), 0f)
        // 150 Гц относится к Low, 160 Гц — уже Mid
        val lowEdge = bandEnergies(fft(1024, 15 to (64 to 0)), 10_240_000)
        assertTrue(lowEdge[0] > 0f)
        assertEquals(0f, lowEdge[1], 0f)
        val midEdge = bandEnergies(fft(1024, 16 to (64 to 0)), 10_240_000)
        assertEquals(0f, midEdge[0], 0f)
        assertTrue(midEdge[1] > 0f)
        // 1000 Гц — Mid, 1010 Гц — вне полос
        assertTrue(bandEnergies(fft(1024, 100 to (64 to 0)), 10_240_000)[1] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 101 to (64 to 0)), 10_240_000), 0f)
        // ~3015 Гц — High, ~2972 Гц — вне полос
        assertTrue(bandEnergies(fft(1024, 70 to (64 to 0)), 44_100_000)[2] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 69 to (64 to 0)), 44_100_000), 0f)
        // ~9991 Гц — High, ~10034 Гц — вне полос
        assertTrue(bandEnergies(fft(1024, 232 to (64 to 0)), 44_100_000)[2] > 0f)
        assertArrayEquals(FloatArray(3), bandEnergies(fft(1024, 233 to (64 to 0)), 44_100_000), 0f)
    }
    @Test fun bandEnergyUsesMeanPower() {
        // Три низких бина по 64: средний RMS = 64, после нормировки 0.5
        val low = bandEnergies(fft(1024, 1 to (64 to 0), 2 to (64 to 0), 3 to (64 to 0)), 44_100_000)
        assertEquals(0.5f, low[0], 0.00001f)
    }
    @Test fun spectralFluxCountsOnlyRises() {
        val previous = floatArrayOf(0.2f, 0.4f, 0.3f)
        val current = floatArrayOf(0.5f, 0.1f, 0.3f)
        assertEquals(0.3f, spectralFlux(current, previous), 0.00001f)
    }
    @Test fun spectralFluxSumsWeightedBands() {
        val previous = floatArrayOf(0f, 0f, 0f)
        val current = floatArrayOf(0.2f, 0.1f, 0.05f)
        // 0.2 * 1.0 + 0.1 * 1.3 + 0.05 * 2.0
        assertEquals(0.43f, spectralFlux(current, previous), 0.00001f)
        assertEquals(1f, spectralFlux(floatArrayOf(1f, 1f, 1f), previous), 0f)
    }
    @Test fun detectorFirstFrameIsBaseline() {
        val detector = TransientDetector()
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0), 3 to (64 to 0))
        assertEquals(0f, detector.process(kick, 44_100_000), 0f)
        assertEquals(0f, detector.process(kick, 44_100_000), 0.00001f)
        detector.reset()
        assertEquals(0f, detector.process(kick, 44_100_000), 0f)
    }
    @Test fun detectorHitThenSilence() {
        val detector = TransientDetector()
        val quiet = ByteArray(1024)
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0), 3 to (64 to 0))
        detector.process(quiet, 44_100_000)
        val hit = detector.process(kick, 44_100_000)
        assertTrue(hit > 0.4f)
        assertEquals(0f, detector.process(kick, 44_100_000), 0.00001f)
        assertEquals(0f, detector.process(quiet, 44_100_000), 0.00001f)
    }
    @Test fun transientSpikeThenFastDecay() {
        val detector = TransientDetector()
        val quiet = ByteArray(1024)
        val kick = fft(1024, 1 to (64 to 0), 2 to (64 to 0), 3 to (64 to 0))
        detector.process(quiet, 44_100_000)
        val hit = detector.process(kick, 44_100_000)
        assertTrue(hit > 0.4f)

        val envelope = AudioEnvelope()
        envelope.update(0f, 0)
        val peak = envelope.update(hit, 8_000_000)
        assertTrue(peak > hit * 0.6f)
        val decayed = envelope.update(0f, 108_000_000)
        assertTrue(decayed < peak * 0.4f)
        assertTrue(decayed > 0f)
    }
    @Test fun attackUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0)
        assertEquals((1 - exp(-1.0)).toFloat(), envelope.update(1f, 8_000_000), 0.00001f)
    }
    @Test fun releaseUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0); val peak = envelope.update(1f, 150_000_000)
        assertEquals(peak * exp(-1.0).toFloat(), envelope.update(0f, 250_000_000), 0.00001f)
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
        assertTrue(envelope.update(sample.freshValue(2_000_000_000), 2_000_000_000) < 0.00001f)
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
