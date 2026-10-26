package com.my.music.core.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class BassDspTest {
    private fun fft(size: Int = 1024, vararg bins: Pair<Int, Pair<Int, Int>>) = ByteArray(size).apply {
        bins.forEach { (bin, complex) -> this[2 * bin] = complex.first.toByte(); this[2 * bin + 1] = complex.second.toByte() }
    }

    @Test fun signedComplexRmsUsesBothComponents() {
        val data = fft(1024, 1 to (-64 to 64), 2 to (-64 to 64), 3 to (-64 to 64), 4 to (-64 to 64))
        assertEquals(sqrt(0.5).toFloat(), bassRms(data, 44_100_000), 0.00001f)
    }
    @Test fun sampleRateIsMilliHertz() {
        assertEquals(0.25f, bassRms(fft(1024, 4 to (64 to 0)), 44_100_000), 0.00001f)
        assertEquals(0f, bassRms(fft(1024, 4 to (64 to 0)), 48_000_000), 0f)
    }
    @Test fun fftSizeChangesFrequencySelection() {
        assertEquals(0f, bassRms(fft(512, 3 to (64 to 0)), 48_000_000), 0f)
        assertTrue(bassRms(fft(1024, 3 to (64 to 0)), 48_000_000) > 0f)
    }
    @Test fun dcAndNyquistExcluded() {
        val data = ByteArray(1024)
        data[0] = 127; data[1] = -128
        assertEquals(0f, bassRms(data, 44_100_000), 0f)
    }
    @Test fun upperBinsExcluded() {
        assertEquals(0f, bassRms(fft(1024, 5 to (127 to -128), 11 to (127 to 127)), 44_100_000), 0f)
    }
    @Test fun boundariesAreInclusive() {
        assertEquals((sqrt(2.0 / 15.0) * 0.5).toFloat(), bassRms(fft(1024, 4 to (64 to 0), 18 to (64 to 0)), 10_240_000), 0.00001f)
    }
    @Test fun noBassBinsReturnsZero() { assertEquals(0f, bassRms(ByteArray(128), 48_000_000), 0f) }
    @Test fun invalidInputsReturnZero() {
        listOf(ByteArray(0), ByteArray(2), ByteArray(7)).forEach { assertEquals(0f, bassRms(it, 44_100_000), 0f) }
        assertEquals(0f, bassRms(ByteArray(1024), 0), 0f)
    }
    @Test fun saturationClamped() {
        val data = fft(1024, 1 to (-128 to -128), 2 to (-128 to -128), 3 to (-128 to -128), 4 to (-128 to -128))
        assertEquals(1f, bassRms(data, 44_100_000), 0f)
    }
    @Test fun attackUsesTimeConstant() {
        val envelope = BassEnvelope(); envelope.update(1f, 0)
        assertEquals((1 - exp(-1.0)).toFloat(), envelope.update(1f, 15_000_000), 0.00001f)
    }
    @Test fun releaseUsesTimeConstant() {
        val envelope = BassEnvelope(); envelope.update(1f, 0); val peak = envelope.update(1f, 150_000_000)
        assertEquals(peak * exp(-1.0).toFloat(), envelope.update(0f, 290_000_000), 0.00001f)
    }
    @Test fun updateCadenceDoesNotChangeEnvelope() {
        val once = BassEnvelope(); once.update(1f, 0)
        val many = BassEnvelope(); many.update(1f, 0)
        for (i in 1..100) many.update(1f, i * 1_000_000L)
        assertEquals(once.update(1f, 100_000_000), many.value, 0.00001f)
    }
    @Test fun nonMonotonicClockDoesNotMoveEnvelope() {
        val envelope = BassEnvelope(); envelope.update(1f, 100); val value = envelope.update(1f, 15_000_100)
        assertEquals(value, envelope.update(0f, 50), 0f)
        assertEquals(value, envelope.update(0f, 15_000_100), 0f)
    }
    @Test fun staleCallbacksDecayToZero() {
        val envelope = BassEnvelope(); envelope.update(0f, 0)
        val sample = BassSample(1f, 0)
        assertTrue(envelope.update(sample.freshValue(200_000_000), 200_000_000) > 0.9f)
        assertEquals(0f, sample.freshValue(250_000_001), 0f)
        assertTrue(envelope.update(sample.freshValue(2_000_000_000), 2_000_000_000) < 0.00001f)
    }
    @Test fun missingCallbacksAndFutureTimestampAreZero() {
        assertEquals(0f, BassSample().freshValue(0), 0f)
        assertEquals(0f, BassSample(1f, 100).freshValue(50), 0f)
    }
    @Test fun nonFiniteTargetDoesNotPoisonEnvelope() {
        val envelope = BassEnvelope(); envelope.update(1f, 0); envelope.update(1f, 15_000_000)
        assertTrue(envelope.update(Float.NaN, 30_000_000).isFinite())
    }
}
