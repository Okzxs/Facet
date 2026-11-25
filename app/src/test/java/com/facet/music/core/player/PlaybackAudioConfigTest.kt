package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioConfigTest {

    @Test
    fun `fade is disabled at zero`() {
        assertEquals(1f, PlaybackAudioConfig.fadeVolume(5_000, 200_000, 0, true), 0.001f)
    }

    @Test
    fun `fade in ramps up`() {
        assertEquals(0f, PlaybackAudioConfig.fadeVolume(0, 200_000, 3_000, true), 0.001f)
        assertEquals(0.5f, PlaybackAudioConfig.fadeVolume(1_500, 200_000, 3_000, true), 0.01f)
        assertEquals(1f, PlaybackAudioConfig.fadeVolume(10_000, 200_000, 3_000, true), 0.001f)
    }

    @Test
    fun `fade out only before the next track`() {
        assertEquals(0.5f, PlaybackAudioConfig.fadeVolume(198_500, 200_000, 3_000, true), 0.01f)
        assertEquals(1f, PlaybackAudioConfig.fadeVolume(198_500, 200_000, 3_000, false), 0.001f)
    }

    @Test
    fun `fade never exceeds one`() {
        val value = PlaybackAudioConfig.fadeVolume(100_000, 200_000, 3_000, true)
        assertTrue(value in 0f..1f)
    }

    @Test
    fun `fade options are sanitized`() {
        assertEquals(0, PlaybackAudioConfig.sanitizeFade(1_234))
        assertEquals(3_000, PlaybackAudioConfig.sanitizeFade(3_000))
    }

    @Test
    fun `buffer profiles grow from small to large`() {
        val small = PlaybackAudioConfig.bufferProfile("SMALL")
        val medium = PlaybackAudioConfig.bufferProfile("MEDIUM")
        val large = PlaybackAudioConfig.bufferProfile("LARGE")

        assertTrue(small.maxBufferMs < medium.maxBufferMs)
        assertTrue(medium.maxBufferMs < large.maxBufferMs)
        assertTrue(small.minBufferMs < large.minBufferMs)
        assertEquals(medium, PlaybackAudioConfig.bufferProfile("unknown"))
    }
}
