package com.my.music.core.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

class AudioDspTest {
    private fun fft(size: Int = 1024, vararg bins: Pair<Int, Pair<Int, Int>>) = ByteArray(size).apply {
        bins.forEach { (bin, complex) -> this[2 * bin] = complex.first.toByte(); this[2 * bin + 1] = complex.second.toByte() }
    }

    @Test fun signedComplexRmsUsesBothComponents() {
        val real = audioRms(fft(1024, 4 to (64 to 0)), 44_100_000)
        val imaginary = audioRms(fft(1024, 4 to (0 to 64)), 44_100_000)
        val both = audioRms(fft(1024, 4 to (-64 to 64)), 44_100_000)
        assertEquals(real, imaginary, 0f)
        assertEquals(real * sqrt(2f), both, 0.00001f)
    }
    @Test fun synthAndHatsAboveBassBandAreCounted() {
        // ~5.1 кГц и ~10.2 кГц: раньше эти бины игнорировались
        assertTrue(audioRms(fft(1024, 118 to (64 to 0)), 44_100_000) > 0f)
        assertTrue(audioRms(fft(1024, 236 to (64 to 0)), 44_100_000) > 0f)
    }
    @Test fun fullSpectrumTreatsLowsAndHighsEqually() {
        val low = audioRms(fft(1024, 4 to (64 to 0)), 44_100_000)
        val high = audioRms(fft(1024, 118 to (64 to 0)), 44_100_000)
        assertEquals(low, high, 0f)
    }
    @Test fun meanRmsAveragesAcrossBand() {
        // Один бин 64 при 371 бине полосы: 64 / sqrt(371) / 128
        assertEquals(0.025958f, audioRms(fft(1024, 4 to (64 to 0)), 44_100_000), 0.00001f)
    }
    @Test fun spectrumBoundariesAreInclusive() {
        // 30 Гц отсекается, 40 Гц уже считается
        assertEquals(0f, audioRms(fft(1024, 3 to (64 to 0)), 10_240_000), 0f)
        assertTrue(audioRms(fft(1024, 4 to (64 to 0)), 10_240_000) > 0f)
        // ~15.98 кГц считается, ~16.02 кГц отсекается
        assertTrue(audioRms(fft(1024, 371 to (64 to 0)), 44_100_000) > 0f)
        assertEquals(0f, audioRms(fft(1024, 372 to (64 to 0)), 44_100_000), 0f)
    }
    @Test fun fftSizeChangesFrequencySelection() {
        assertEquals(0f, audioRms(fft(512, 255 to (64 to 0)), 48_000_000), 0f)
        assertTrue(audioRms(fft(2048, 255 to (64 to 0)), 48_000_000) > 0f)
    }
    @Test fun dcAndNyquistExcluded() {
        val data = ByteArray(1024)
        data[0] = 127; data[1] = -128
        assertEquals(0f, audioRms(data, 44_100_000), 0f)
    }
    @Test fun silentSpectrumReturnsZero() { assertEquals(0f, audioRms(ByteArray(128), 48_000_000), 0f) }
    @Test fun invalidInputsReturnZero() {
        listOf(ByteArray(0), ByteArray(2), ByteArray(7)).forEach { assertEquals(0f, audioRms(it, 44_100_000), 0f) }
        assertEquals(0f, audioRms(ByteArray(1024), 0), 0f)
    }
    @Test fun saturationClamped() {
        // Все бины полной шкалы: средний RMS выше единицы, выход ограничен
        assertEquals(1f, audioRms(ByteArray(1024) { 127 }, 44_100_000), 0f)
    }
    @Test fun attackUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0)
        assertEquals((1 - exp(-1.0)).toFloat(), envelope.update(1f, 15_000_000), 0.00001f)
    }
    @Test fun releaseUsesTimeConstant() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0); val peak = envelope.update(1f, 150_000_000)
        assertEquals(peak * exp(-1.0).toFloat(), envelope.update(0f, 290_000_000), 0.00001f)
    }
    @Test fun updateCadenceDoesNotChangeEnvelope() {
        val once = AudioEnvelope(); once.update(1f, 0)
        val many = AudioEnvelope(); many.update(1f, 0)
        for (i in 1..100) many.update(1f, i * 1_000_000L)
        assertEquals(once.update(1f, 100_000_000), many.value, 0.00001f)
    }
    @Test fun nonMonotonicClockDoesNotMoveEnvelope() {
        val envelope = AudioEnvelope(); envelope.update(1f, 100); val value = envelope.update(1f, 15_000_100)
        assertEquals(value, envelope.update(0f, 50), 0f)
        assertEquals(value, envelope.update(0f, 15_000_100), 0f)
    }
    @Test fun staleCallbacksDecayToZero() {
        val envelope = AudioEnvelope(); envelope.update(0f, 0)
        val sample = AudioSample(1f, 0)
        assertTrue(envelope.update(sample.freshValue(200_000_000), 200_000_000) > 0.9f)
        assertEquals(0f, sample.freshValue(250_000_001), 0f)
        assertTrue(envelope.update(sample.freshValue(2_000_000_000), 2_000_000_000) < 0.00001f)
    }
    @Test fun missingCallbacksAndFutureTimestampAreZero() {
        assertEquals(0f, AudioSample().freshValue(0), 0f)
        assertEquals(0f, AudioSample(1f, 100).freshValue(50), 0f)
    }
    @Test fun nonFiniteTargetDoesNotPoisonEnvelope() {
        val envelope = AudioEnvelope(); envelope.update(1f, 0); envelope.update(1f, 15_000_000)
        assertTrue(envelope.update(Float.NaN, 30_000_000).isFinite())
    }
}
