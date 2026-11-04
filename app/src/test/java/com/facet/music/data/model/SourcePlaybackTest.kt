package com.facet.music.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePlaybackTest {

    @Test
    fun `deezer apple and spotify are metadata only`() {
        assertTrue(Source.DEEZER.isMetadataOnly)
        assertTrue(Source.APPLE.isMetadataOnly)
        assertTrue(Source.SPOTIFY.isMetadataOnly)
    }

    @Test
    fun `soundcloud youtube and local have their own stream`() {
        assertFalse(Source.SOUNDCLOUD.isMetadataOnly)
        assertFalse(Source.YOUTUBE.isMetadataOnly)
        assertFalse(Source.LOCAL.isMetadataOnly)
    }
}
