package com.facet.music.core.player

import kotlin.math.ln
import kotlin.math.pow

/**
 * Настройка строя (детюн) и вариации воспроизведения.
 *
 * Детюн задаётся частотой ноты «ля» первой октавы: 440 Гц — стандарт,
 * 432 Гц — «барочный» строй и т. п. ExoPlayer умеет менять питч независимо
 * от скорости, поэтому коэффициент считается как 2^(cents / 1200):
 * один полутон — это 100 центов, октава — 1200.
 */
object PlaybackTuning {

    /** Частота стандартного строя, относительно которой считаются центы. */
    const val REFERENCE_HZ: Float = 440f

    /** Доступные строи в герцах: от низкого к высокому. */
    val FREQUENCIES: List<Int> = listOf(428, 430, 432, 440, 448, 450, 453)

    /** Сдвиг в центах относительно [REFERENCE_HZ]; 440 Гц даёт 0. */
    fun cents(frequencyHz: Float): Double =
        1200.0 * ln(frequencyHz.toDouble() / REFERENCE_HZ) / ln(2.0)

    /** Коэффициент питча для [PlaybackParameters]: 440 Гц → 1.0. */
    fun pitchFactor(frequencyHz: Float): Float =
        2.0.pow(cents(frequencyHz) / 1200.0).toFloat()
}

/**
 * Готовые вариации трека: скорость и питч подаются в ExoPlayer вместе.
 *
 * Reverb в эту модель не входит: `PlaybackParameters` умеет только скорость
 * и питч, а аудиоэффекты (PresetReverb) требуют отдельного аудиосеанса
 * и разрешения MODIFY_AUDIO_SETTINGS. Поэтому «Slowed + Reverb» пока
 * реализован как замедление с понижением питча.
 */
enum class PlaybackVariation(
    val speed: Float,
    val pitch: Float,
) {
    NORMAL(speed = 1.0f, pitch = 1.0f),
    SPEED_UP(speed = 1.25f, pitch = 1.2f),
    SLOWED(speed = 0.8f, pitch = 0.85f),
    NIGHTCORE(speed = 1.3f, pitch = 1.3f),
}
