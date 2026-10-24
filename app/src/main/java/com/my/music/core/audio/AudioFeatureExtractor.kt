package com.my.music.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Извлекает PCM из локального аудиофайла для YAMNet.
 *
 * Важно для YAMNet:
 * - только моно;
 * - частота дискретизации 16 000 Гц;
 * - значения Float в диапазоне [-1, 1] (PCM16 / 32768f);
 * - первые 30 секунд трека, без peak-normalization.
 *
 * MediaCodec отдаёт PCM в родной частоте файла (обычно 44.1 или 48 кГц),
 * поэтому после декодирования выполняется даунмикс и линейный ресемплинг.
 * Линейный ресемплинг — осознанный компромисс Phase 1: он дешёвый и
 * сохраняет достаточно информации для эмбеддинга, хотя не подавляет
 * высокочастотный алиасинг идеально.
 */
@Singleton
class AudioFeatureExtractor @Inject constructor() {

    suspend fun extractPcm(file: File): FloatArray = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0) {
            "Локальный аудиофайл не найден: ${file.absolutePath}"
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = findAudioTrack(extractor)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("У аудиодорожки нет MIME-типа")
            val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(sourceSampleRate > 0 && sourceChannels > 0) {
                "Некорректный аудиоформат: rate=$sourceSampleRate, channels=$sourceChannels"
            }

            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(mimeType)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val collector = MonoPcmCollector(
                sourceSampleRate = sourceSampleRate,
                sourceChannels = sourceChannels,
            )
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleAfterInput = 0

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: error("MediaCodec не вернул input buffer")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs > MAX_DURATION_US) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(
                    bufferInfo,
                    DEQUEUE_TIMEOUT_US,
                )
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            idleAfterInput++
                            if (idleAfterInput > MAX_IDLE_AFTER_INPUT) {
                                Log.w(TAG, "Декодер не прислал EOS, завершаем чтение")
                                outputDone = true
                            }
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        collector.updateOutputFormat(decoder.outputFormat)
                    }

                    outputIndex >= 0 -> {
                        idleAfterInput = 0
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            collector.append(
                                buffer = outputBuffer,
                                offset = bufferInfo.offset,
                                length = bufferInfo.size,
                            )
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val pcm = collector.resampleToTarget()
            Log.d(
                TAG,
                "PCM готов: ${pcm.size} сэмплов, " +
                    "${pcm.size * 1000L / TARGET_SAMPLE_RATE_HZ} мс",
            )
            pcm
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        error("В файле нет аудиодорожки")
    }

    /**
     * Накопитель PCM: даунмикс в моно + ресемплинг в 16 кГц.
     *
     * Храним не больше 30 секунд исходной частоты. Для 48 кГц это ~5.7 МБ
     * FloatArray, что приемлемо для фонового воркера.
     */
    private class MonoPcmCollector(
        sourceSampleRate: Int,
        sourceChannels: Int,
    ) {
        private var sampleRate = sourceSampleRate
        private var channels = sourceChannels
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        private val sourceLimit = sourceSampleRate * MAX_DURATION_SECONDS

        private var samples = FloatArray(minOf(sourceLimit, INITIAL_CAPACITY))
        private var size = 0

        fun updateOutputFormat(format: MediaFormat) {
            val newRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val newChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (newRate > 0) sampleRate = newRate
            if (newChannels > 0) channels = newChannels
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
        }

        fun append(buffer: ByteBuffer, offset: Int, length: Int) {
            if (size >= sourceLimit || channels <= 0 || length <= 0) return
            require(
                pcmEncoding == AudioFormat.ENCODING_PCM_16BIT ||
                    pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT,
            ) {
                "Неподдерживаемый PCM encoding: $pcmEncoding"
            }

            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            data.position(offset)
            data.limit((offset + length).coerceAtMost(buffer.limit()))

            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                Float.SIZE_BYTES
            } else {
                Short.SIZE_BYTES
            }
            val frameCount = data.remaining() / (bytesPerSample * channels)
            if (frameCount <= 0) return

            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val floats = data.asFloatBuffer()
                for (frame in 0 until frameCount) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        sum += floats.get(frame * channels + channel)
                    }
                    add(sum / channels)
                }
            } else {
                val shorts = data.asShortBuffer()
                for (frame in 0 until frameCount) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        sum += shorts.get(frame * channels + channel) / 32768f
                    }
                    add(sum / channels)
                }
            }
        }

        private fun add(sample: Float) {
            if (size >= sourceLimit) return
            if (size == samples.size) {
                samples = samples.copyOf(minOf(sourceLimit, samples.size * 2))
            }
            samples[size++] = sample
        }

        fun resampleToTarget(): FloatArray {
            if (size == 0 || sampleRate <= 0) return FloatArray(0)
            if (sampleRate == TARGET_SAMPLE_RATE_HZ) return samples.copyOf(size)

            val ratio = sampleRate.toDouble() / TARGET_SAMPLE_RATE_HZ
            val targetSize = (size / ratio).roundToInt()
                .coerceAtLeast(1)
                .coerceAtMost(TARGET_SAMPLE_RATE_HZ * MAX_DURATION_SECONDS)

            val result = FloatArray(targetSize)
            for (index in result.indices) {
                val position = index * ratio
                val leftIndex = position.toInt().coerceAtMost(size - 1)
                val rightIndex = (leftIndex + 1).coerceAtMost(size - 1)
                val fraction = (position - leftIndex).toFloat()
                result[index] = samples[leftIndex] +
                    (samples[rightIndex] - samples[leftIndex]) * fraction
            }
            return result
        }

        private companion object {
            const val INITIAL_CAPACITY = 16_000 * 2
        }
    }

    companion object {
        private const val TAG = "AudioFeatureExtractor"
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
        private const val MAX_DURATION_SECONDS = 30
        private const val MAX_DURATION_US = MAX_DURATION_SECONDS * 1_000_000L
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MAX_IDLE_AFTER_INPUT = 300
    }
}
