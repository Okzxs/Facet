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
        if (!enabled) {
            val size = inputBuffer.remaining()
            val output = replaceOutputBuffer(size)
            output.put(inputBuffer).flip()
            return
        }
        val size = inputBuffer.remaining()
        val output = replaceOutputBuffer(size)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(size / 4)
                inputBuffer.asFloatBuffer().get(floats)
                PeakLimiter.process(floats, state)
                output.asFloatBuffer().put(floats)
                output.position(0)
                output.limit(size)
            }
            C.ENCODING_PCM_16BIT -> {
                val shorts = ShortArray(size / 2)
                inputBuffer.asShortBuffer().get(shorts)
                val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
                PeakLimiter.process(floats, state)
                for (index in shorts.indices) {
                    shorts[index] = (floats[index] * 32767f).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                output.asShortBuffer().put(shorts)
                output.position(0)
                output.limit(size)
            }
        }
        inputBuffer.position(inputBuffer.limit())
    }

    override fun onFlush() {
        state.gain = 1f
    }
}
