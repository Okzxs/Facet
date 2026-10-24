package com.my.music.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import android.util.Log
import java.nio.FloatBuffer

/**
 * Реальная ONNX Runtime-сессия для YAMNet из assets.
 *
 * Модель `audiomagic/yamnet-onnx` (Apache 2.0) принимает сырой моно PCM
 * 16 кГц в [-1, 1] в тензор `waveform` и отдаёт:
 * - output_0 — вероятности 521 класса AudioSet;
 * - output_1 — эмбеддинги формы [frames, 1024];
 * - output_2 — спектрограмму.
 *
 * Для рекомендаций используется output_1. Mel-фронтенд уже находится
 * внутри модели, поэтому снаружи не нужны FFT и log-mel.
 */
internal class OnnxAudioModelSession(
    private val assets: AssetManager,
) : AudioModelSession {

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession by lazy { createSession() }

    override fun run(samples: FloatArray): Array<FloatArray> {
        val input = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(samples),
            longArrayOf(samples.size.toLong()),
        )

        input.use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                val value = result.get(OUTPUT_EMBEDDING_INDEX).value
                @Suppress("UNCHECKED_CAST")
                val frames = value as Array<FloatArray>

                // Копируем данные до закрытия OrtSession.Result: Java-массив
                // не должен зависеть от времени жизни нативного тензора.
                return Array(frames.size) { index -> frames[index].copyOf() }
            }
        }
    }

    private fun createSession(): OrtSession {
        val modelBytes = assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        return try {
            // Для слабого телефона одного intra-op потока достаточно:
            // модель небольшая, а лишние потоки конкурируют с плеером.
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(modelBytes, options).also {
                Log.d(TAG, "YAMNet загружена: ${modelBytes.size} байт")
            }
        } finally {
            options.close()
        }
    }

    companion object {
        const val MODEL_ASSET = "models/yamnet.onnx"
        private const val INPUT_NAME = "waveform"
        private const val OUTPUT_EMBEDDING_INDEX = 1
        private const val TAG = "AudioEmbedding"
    }
}
