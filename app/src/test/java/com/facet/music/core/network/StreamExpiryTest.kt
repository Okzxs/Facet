package com.facet.music.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamExpiryTest {

    @Test
    fun `expire из YouTube переводится в миллисекунды`() {
        assertEquals(
            1_790_000_000_000L,
            StreamExpiry.fromUrl("https://rr1.googlevideo.com/a?expire=1790000000&ip=1"),
        )
    }

    @Test
    fun `expires из SoundCloud тоже понимается`() {
        assertEquals(
            1_790_001_234_000L,
            StreamExpiry.fromUrl("https://playback.soundcloud.cloud/a?expires=1790001234"),
        )
    }

    @Test
    fun `без параметра срока жизни null`() {
        assertNull(StreamExpiry.fromUrl("https://example.com/audio.mp3"))
    }
}
