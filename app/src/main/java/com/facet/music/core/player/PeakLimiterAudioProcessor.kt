package com.facet.music.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

@UnstableApi
class PeakLimiterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = true

    private val state = PeakLimiterState()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val bytes = ByteArray(size)
        inputBuffer.duplicate().get(bytes)

        val output = replaceOutputBuffer(size)
        val processed = enabled && size > 0 && when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> applyFloat(bytes, output)
            C.ENCODING_PCM_16BIT -> applyShort(bytes, output)
            else -> false
        }
        if (!processed) {
            output.put(bytes)
        }
        output.position(0)
        output.limit(size)
        inputBuffer.position(inputBuffer.limit())
    }

    private fun applyFloat(bytes: ByteArray, output: ByteBuffer): Boolean {
        if (bytes.size % 4 != 0) return false
        val source = ByteBuffer.wrap(bytes).asFloatBuffer()
        val floats = FloatArray(source.remaining())
        source.get(floats)
        PeakLimiter.process(floats, state)
        output.asFloatBuffer().put(floats)
        return true
    }

    private fun applyShort(bytes: ByteArray, output: ByteBuffer): Boolean {
        if (bytes.size % 2 != 0) return false
        val source = ByteBuffer.wrap(bytes).asShortBuffer()
        val shorts = ShortArray(source.remaining())
        source.get(shorts)
        val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
        PeakLimiter.process(floats, state)
        for (index in shorts.indices) {
            shorts[index] = (floats[index] * 32767f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        output.asShortBuffer().put(shorts)
        return true
    }

    override fun onFlush() {
        state.gain = 1f
    }
}
