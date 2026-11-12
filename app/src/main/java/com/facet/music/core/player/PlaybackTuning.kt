package com.facet.music.core.player

import kotlin.math.ln
import kotlin.math.pow

object PlaybackTuning {

    const val REFERENCE_HZ: Float = 440f

    val FREQUENCIES: List<Int> = listOf(428, 430, 432, 440, 448, 450, 453)

    fun cents(frequencyHz: Float): Double =
        1200.0 * ln(frequencyHz.toDouble() / REFERENCE_HZ) / ln(2.0)

    fun pitchFactor(frequencyHz: Float): Float =
        2.0.pow(cents(frequencyHz) / 1200.0).toFloat()
}

enum class PlaybackVariation(
    val speed: Float,
    val pitch: Float,
) {
    NORMAL(speed = 1.0f, pitch = 1.0f),
    SPEED_UP(speed = 1.25f, pitch = 1.2f),
    SLOWED(speed = 0.8f, pitch = 0.85f),
    NIGHTCORE(speed = 1.3f, pitch = 1.3f),
}
