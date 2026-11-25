package com.facet.music.core.player

import android.content.Context
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

@UnstableApi
class FacetRenderersFactory(
    context: Context,
    private val silenceSkipping: SilenceSkippingAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val chain = DefaultAudioSink.DefaultAudioProcessorChain(
            arrayOf(),
            silenceSkipping,
            SonicAudioProcessor(),
        )
        return DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(chain)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
