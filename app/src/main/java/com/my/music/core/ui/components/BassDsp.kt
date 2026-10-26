package com.my.music.core.ui.components

import kotlin.math.exp
import kotlin.math.sqrt

/** RMS комплексных signed-бинов 40–180 Гц, без DC и Nyquist.
 * Нормализация на 128 соответствует масштабу signed-byte компоненты Visualizer;
 * это относительная энергия для декора, не калиброванный уровень громкости.
 */
internal fun bassRms(fft: ByteArray, samplingRateMilliHz: Int): Float {
    if (fft.size < 4 || fft.size % 2 != 0 || samplingRateMilliHz <= 0) return 0f
    val sampleRate = samplingRateMilliHz / 1000.0
    var sum = 0.0
    var count = 0
    for (bin in 1 until fft.size / 2) {
        val frequency = bin * sampleRate / fft.size
        if (frequency !in 40.0..180.0) continue
        val real = fft[2 * bin].toDouble()
        val imaginary = fft[2 * bin + 1].toDouble()
        sum += real * real + imaginary * imaginary
        count++
    }
    return if (count == 0) 0f else (sqrt(sum / count) / 128.0).toFloat().coerceIn(0f, 1f)
}

/** Одна временная огибающая; время приходит от монотонных часов, не от числа FFT. */
internal class BassEnvelope {
    var value: Float = 0f
        private set
    private var previousNanos: Long? = null

    fun update(target: Float, nowNanos: Long): Float {
        val previous = previousNanos
        if (previous == null) {
            previousNanos = nowNanos
            return value
        }
        if (nowNanos <= previous) return value
        previousNanos = nowNanos
        val safeTarget = if (target.isFinite()) target.coerceIn(0f, 1f) else 0f
        val tau = if (safeTarget > value) 0.015 else 0.140
        val dt = (nowNanos - previous) / 1_000_000_000.0
        value += (safeTarget - value) * (1.0 - exp(-dt / tau)).toFloat()
        return value
    }
}

internal data class BassSample(val value: Float = 0f, val capturedNanos: Long = Long.MIN_VALUE) {
    fun freshValue(nowNanos: Long): Float =
        if (capturedNanos != Long.MIN_VALUE && nowNanos >= capturedNanos &&
            nowNanos - capturedNanos <= 250_000_000L
        ) value else 0f
}
