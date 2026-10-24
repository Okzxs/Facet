package com.my.music.core.audio

import kotlin.math.sqrt

/**
 * Косинусное сходство двух векторов.
 *
 * Возвращает значение в [-1, 1]. Для разных размерностей или нулевых
 * векторов возвращает 0, чтобы диагностический лог не падал.
 */
internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.isEmpty() || a.size != b.size) return 0f

    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (index in a.indices) {
        val left = a[index].toDouble()
        val right = b[index].toDouble()
        dot += left * right
        normA += left * left
        normB += right * right
    }

    if (normA < 1e-12 || normB < 1e-12) return 0f
    val similarity = dot / (sqrt(normA) * sqrt(normB))
    return similarity.toFloat().coerceIn(-1f, 1f)
}
