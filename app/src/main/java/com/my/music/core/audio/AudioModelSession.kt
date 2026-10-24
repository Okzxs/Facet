package com.my.music.core.audio

/**
 * Низкоуровневая ONNX-сессия YAMNet.
 *
 * Возвращает эмбеддинги по кадрам: каждый элемент массива — FloatArray(1024).
 * Агрегация и нормализация выполняются в [AudioEmbeddingEngine], поэтому
 * эту границу удобно подменять в JVM-тестах без Android JNI.
 */
internal fun interface AudioModelSession {
    fun run(samples: FloatArray): Array<FloatArray>
}
