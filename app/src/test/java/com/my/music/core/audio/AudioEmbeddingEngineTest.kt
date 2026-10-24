package com.my.music.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * JVM-тест обёртки без загрузки Android ONNX JNI.
 *
 * Реальный инференс здесь не нужен: проверяем контракт движка —
 * размерность, усреднение кадров и L2-нормализацию.
 */
class AudioEmbeddingEngineTest {

    @Test
    fun `returns normalized 1024-dimensional vector`() {
        val engine = AudioEmbeddingEngine.forTesting { _ ->
            arrayOf(flatFrame(1f), flatFrame(3f))
        }

        val result = engine.embed(FloatArray(16_000))

        assertEquals(AudioEmbeddingEngine.EMBEDDING_SIZE, result.size)
        val norm = sqrt(result.sumOf { it.toDouble() * it.toDouble() })
        assertEquals(1.0, norm, 1e-5)

        val expectedComponent = 1f / sqrt(AudioEmbeddingEngine.EMBEDDING_SIZE.toFloat())
        assertEquals(expectedComponent, result.first(), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty pcm`() {
        val engine = AudioEmbeddingEngine.forTesting { _ -> arrayOf(flatFrame(1f)) }
        engine.embed(FloatArray(0))
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects wrong model output size`() {
        val engine = AudioEmbeddingEngine.forTesting { _ ->
            arrayOf(FloatArray(16) { 1f })
        }
        engine.embed(FloatArray(16_000))
    }

    private fun flatFrame(value: Float): FloatArray =
        FloatArray(AudioEmbeddingEngine.EMBEDDING_SIZE) { value }
}
