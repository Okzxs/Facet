package com.my.music.core.ui.components

import kotlin.math.exp
import kotlin.math.sqrt

/** Нижняя граница спектра: инфразвук и гул не должны раскачивать волну. */
private const val MIN_FREQUENCY_HZ = 40.0

/** Верхняя граница: 16 кГц покрывает синты, хэты и воздух, отсекая ультразвуковой мусор. */
private const val MAX_FREQUENCY_HZ = 16_000.0

/**
 * Полная энергия спектра (Full Spectrum RMS) по бинам 40 Гц–16 кГц, без DC и Nyquist.
 *
 * Корень из среднего квадрата комплексных signed-бинов: бас, синты и хэты вносят
 * равный вклад, поэтому волна реагирует на общую плотность звука, а не только на
 * низы. Нормализация на 128 соответствует масштабу signed-byte компоненты
 * Visualizer; это относительная энергия для декора, не калиброванный уровень
 * громкости.
 */
internal fun audioRms(fft: ByteArray, samplingRateMilliHz: Int): Float {
    if (fft.size < 4 || fft.size % 2 != 0 || samplingRateMilliHz <= 0) return 0f
    val sampleRate = samplingRateMilliHz / 1000.0
    var sum = 0.0
    var count = 0
    for (bin in 1 until fft.size / 2) {
        val frequency = bin * sampleRate / fft.size
        if (frequency < MIN_FREQUENCY_HZ || frequency > MAX_FREQUENCY_HZ) continue
        val real = fft[2 * bin].toDouble()
        val imaginary = fft[2 * bin + 1].toDouble()
        sum += real * real + imaginary * imaginary
        count++
    }
    return if (count == 0) 0f else (sqrt(sum / count) / 128.0).toFloat().coerceIn(0f, 1f)
}

/** Одна временная огибающая; время приходит от монотонных часов, не от числа FFT. */
internal class AudioEnvelope {
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
        // Атака 15 мс, спад 140 мс: волна дышит плавно, без судорожных скачков.
        val tau = if (safeTarget > value) 0.015 else 0.140
        val dt = (nowNanos - previous) / 1_000_000_000.0
        value += (safeTarget - value) * (1.0 - exp(-dt / tau)).toFloat()
        return value
    }
}

internal data class AudioSample(val value: Float = 0f, val capturedNanos: Long = Long.MIN_VALUE) {
    fun freshValue(nowNanos: Long): Float =
        if (capturedNanos != Long.MIN_VALUE && nowNanos >= capturedNanos &&
            nowNanos - capturedNanos <= 250_000_000L
        ) value else 0f
}
