package com.my.music.core.audio

import kotlin.math.sqrt

/**
 * Обёртка над ONNX-моделью YAMNet.
 *
 * Модель возвращает эмбеддинг для каждого кадра (обычно ~0.96 с). Для
 * рекомендаций нужен один вектор на трек, поэтому:
 * 1. усредняем эмбеддинги всех кадров;
 * 2. L2-нормализуем результат, чтобы косинусное сходство не зависело
 *    от громкости и длины фрагмента.
 *
 * Вход: моно PCM 16 кГц в [-1, 1] (подготовка — в [AudioFeatureExtractor]).
 * Выход: FloatArray(1024).
 */
class AudioEmbeddingEngine internal constructor(
    private val session: AudioModelSession,
) {

    fun embed(samples: FloatArray): FloatArray {
        require(samples.isNotEmpty()) { "Пустой PCM: нечего анализировать" }

        val frames = session.run(samples)
        check(frames.isNotEmpty()) { "YAMNet не вернула ни одного кадра" }
        check(frames.all { it.size == EMBEDDING_SIZE }) {
            "YAMNet вернула кадр неверного размера: ожидался $EMBEDDING_SIZE"
        }

        val vector = FloatArray(EMBEDDING_SIZE)
        for (frame in frames) {
            for (index in vector.indices) {
                vector[index] += frame[index]
            }
        }

        val scale = 1f / frames.size
        var sumSquares = 0.0
        for (index in vector.indices) {
            vector[index] *= scale
            sumSquares += vector[index].toDouble() * vector[index].toDouble()
        }

        val norm = sqrt(sumSquares).toFloat()
        if (norm > 1e-8f) {
            val inverseNorm = 1f / norm
            for (index in vector.indices) {
                vector[index] *= inverseNorm
            }
        }
        return vector
    }

    companion object {
        /** Размерность эмбеддинга YAMNet. */
        const val EMBEDDING_SIZE = 1024

        /** Фабрика для JVM-тестов с фейковой ONNX-сессией. */
        internal fun forTesting(session: AudioModelSession): AudioEmbeddingEngine =
            AudioEmbeddingEngine(session)
    }
}
