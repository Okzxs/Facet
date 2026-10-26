package com.my.music.core.ui.components

import kotlin.math.exp
import kotlin.math.sqrt

private const val BAND_COUNT = 3

/** Бочка и бас. 150 Гц относится к низкой полосе. */
private const val LOW_MIN_HZ = 40.0
private const val LOW_MAX_HZ = 150.0

/** Снейр, клэп и вокал. */
private const val MID_MIN_HZ = 150.0
private const val MID_MAX_HZ = 1_000.0

/** Хэты и тарелки. */
private const val HIGH_MIN_HZ = 3_000.0
private const val HIGH_MAX_HZ = 10_000.0

/**
 * Веса полос для суммы скачков: компенсируют разное число бинов в полосе
 * и естественный спад энергии высоких частот.
 */
private val BAND_GAINS = floatArrayOf(1.0f, 1.3f, 2.0f)

/**
 * Энергия трёх полос: Low, Mid, High.
 *
 * Для каждой полосы берётся корень из среднего квадрата комплексных signed-бинов,
 * нормированный на 128 (масштаб signed-byte Visualizer). DC, Nyquist и промежутки
 * между полосами не учитываются. Результат — относительная энергия для декора,
 * не калиброванный уровень громкости.
 */
internal fun bandEnergies(fft: ByteArray, samplingRateMilliHz: Int): FloatArray {
    if (fft.size < 4 || fft.size % 2 != 0 || samplingRateMilliHz <= 0) return FloatArray(BAND_COUNT)
    val sampleRate = samplingRateMilliHz / 1000.0
    val sums = DoubleArray(BAND_COUNT)
    val counts = IntArray(BAND_COUNT)
    for (bin in 1 until fft.size / 2) {
        val frequency = bin * sampleRate / fft.size
        val band = when {
            frequency in LOW_MIN_HZ..LOW_MAX_HZ -> 0
            frequency in MID_MIN_HZ..MID_MAX_HZ -> 1
            frequency in HIGH_MIN_HZ..HIGH_MAX_HZ -> 2
            else -> continue
        }
        val real = fft[2 * bin].toDouble()
        val imaginary = fft[2 * bin + 1].toDouble()
        sums[band] += real * real + imaginary * imaginary
        counts[band]++
    }
    return FloatArray(BAND_COUNT) { band ->
        if (counts[band] == 0) 0f
        else (sqrt(sums[band] / counts[band]) / 128.0).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Spectral Flux: сумма положительных приростов энергии полос.
 *
 * Резкий удар поднимает энергию полосы за один кадр — это и есть транзиент.
 * Падение и неизменная энергия вклада не дают, поэтому без нового удара
 * значение сразу возвращается к нулю.
 */
internal fun spectralFlux(current: FloatArray, previous: FloatArray): Float {
    var flux = 0f
    for (band in 0 until BAND_COUNT) {
        val rise = current[band] - previous[band]
        if (rise > 0f) flux += rise * BAND_GAINS[band]
    }
    return flux.coerceIn(0f, 1f)
}

/**
 * Детектор транзиентов между последовательными FFT-кадрами.
 *
 * Первый кадр только запоминает энергию полос: сравнивать ещё не с чем.
 * [reset] вызывается при смене трека, сессии или освобождении Visualizer,
 * чтобы удар из прошлого трека не дал ложный пик в новом.
 */
internal class TransientDetector {
    private var previous = FloatArray(BAND_COUNT)
    private var hasPrevious = false

    @Synchronized
    fun process(fft: ByteArray, samplingRateMilliHz: Int): Float {
        val current = bandEnergies(fft, samplingRateMilliHz)
        val flux = if (hasPrevious) spectralFlux(current, previous) else 0f
        previous = current
        hasPrevious = true
        return flux
    }

    @Synchronized
    fun reset() {
        previous = FloatArray(BAND_COUNT)
        hasPrevious = false
    }
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
        // Атака 8 мс и спад 100 мс: волна резко подпрыгивает на удар и быстро опадает.
        val tau = if (safeTarget > value) 0.008 else 0.100
        val dt = (nowNanos - previous) / 1_000_000_000.0
        value += (safeTarget - value) * (1.0 - exp(-dt / tau)).toFloat()
        return value
    }
}

internal data class AudioSample(val value: Float = 0f, val capturedNanos: Long = Long.MIN_VALUE) {
    /** Значение живёт 120 мс: при 20 Гц FFT-кадров это запас на два пропущенных кадра. */
    fun freshValue(nowNanos: Long): Float =
        if (capturedNanos != Long.MIN_VALUE && nowNanos >= capturedNanos &&
            nowNanos - capturedNanos <= 120_000_000L
        ) value else 0f
}
