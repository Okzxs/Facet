package com.my.music.core.ui.components

import kotlin.math.exp
import kotlin.math.sqrt

private const val BAND_COUNT = 3

/** Бочка: основной удар и низ баса. */
private const val KICK_MIN_HZ = 30.0
private const val KICK_MAX_HZ = 120.0

/** Снейр, клэп и тело вокала; сюда попадает речитатив. */
private const val SNARE_MIN_HZ = 150.0
private const val SNARE_MAX_HZ = 600.0

/** Атака и четкость: щелчок снейра и согласные в вокале. */
private const val PRESENCE_MIN_HZ = 1_500.0
private const val PRESENCE_MAX_HZ = 6_000.0

/** Небольшой акцент на снейре и вокале. */
private val BAND_GAINS = floatArrayOf(1.2f, 1.5f, 1.0f)

/** Окно скользящего среднего: 12 кадров ≈ 600 мс при 20 Гц FFT. */
private const val HISTORY_FRAMES = 12

/** Удар должен превысить среднее в 1.5 раза. */
private const val ONSET_FACTOR = 1.5f

/** Нижний порог: тише этого фоновый шум не двигает волну. */
private const val MIN_ONSET_ENERGY = 0.04f

/**
 * Энергия трёх музыкальных полос: Kick, Snare_Vocal, Presence.
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
            frequency in KICK_MIN_HZ..KICK_MAX_HZ -> 0
            frequency in SNARE_MIN_HZ..SNARE_MAX_HZ -> 1
            frequency in PRESENCE_MIN_HZ..PRESENCE_MAX_HZ -> 2
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
 * Spectral Flux с адаптивным порогом.
 *
 * Считаются только положительные приросты полос, чья текущая энергия превысила
 * порог [thresholds]. Слабые колебания фона и мелкий шум вклада не дают,
 * поэтому волна пульсирует на удары, а не дрожит постоянно.
 */
internal fun spectralFlux(current: FloatArray, previous: FloatArray, thresholds: FloatArray): Float {
    var flux = 0f
    for (band in 0 until BAND_COUNT) {
        val rise = current[band] - previous[band]
        if (rise > 0f && current[band] > thresholds[band]) {
            flux += rise * BAND_GAINS[band]
        }
    }
    return flux.coerceIn(0f, 1f)
}

/**
 * Детектор транзиентов между последовательными FFT-кадрами.
 *
 * Для каждой полосы хранится скользящее среднее энергии за [HISTORY_FRAMES]
 * кадров. Ударом считается только рост, при котором текущая энергия выше
 * среднего в [ONSET_FACTOR] раза и выше [MIN_ONSET_ENERGY]. Первый кадр
 * задаёт базу; [reset] очищает историю при смене трека, сессии или
 * освобождении Visualizer.
 */
internal class TransientDetector {
    private val history = Array(BAND_COUNT) { FloatArray(HISTORY_FRAMES) }
    private val historySums = FloatArray(BAND_COUNT)
    private var historyIndex = 0
    private var historyCount = 0
    private var previous = FloatArray(BAND_COUNT)
    private var hasPrevious = false

    @Synchronized
    fun process(fft: ByteArray, samplingRateMilliHz: Int): Float {
        val current = bandEnergies(fft, samplingRateMilliHz)
        if (!hasPrevious) {
            previous = current
            hasPrevious = true
            push(current)
            return 0f
        }
        val thresholds = FloatArray(BAND_COUNT) { band ->
            val average = if (historyCount == 0) 0f else historySums[band] / historyCount
            maxOf(average * ONSET_FACTOR, MIN_ONSET_ENERGY)
        }
        val flux = spectralFlux(current, previous, thresholds)
        previous = current
        push(current)
        return flux
    }

    @Synchronized
    fun reset() {
        history.forEach { it.fill(0f) }
        historySums.fill(0f)
        historyIndex = 0
        historyCount = 0
        previous.fill(0f)
        hasPrevious = false
    }

    private fun push(current: FloatArray) {
        val slot = historyIndex
        for (band in 0 until BAND_COUNT) {
            historySums[band] += current[band] - history[band][slot]
            history[band][slot] = current[band]
        }
        historyIndex = (slot + 1) % HISTORY_FRAMES
        if (historyCount < HISTORY_FRAMES) historyCount++
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
        // Атака 8 мс и спад 200 мс: волна резко подпрыгивает на удар и плавно опадает.
        val tau = if (safeTarget > value) 0.008 else 0.200
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
