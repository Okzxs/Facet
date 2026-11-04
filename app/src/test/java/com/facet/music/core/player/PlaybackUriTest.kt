package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackUriTest {

    @Test
    fun `track id survives url encoding`() {
        val trackId = "https://soundcloud.com/artist/track?si=abc&utm_source=test"
        val uri = PlaybackUri.forTrack(trackId)

        assertEquals("facet", uri.scheme)
        assertEquals("track", uri.host)
        assertEquals(trackId, PlaybackUri.trackId(uri))
    }

    @Test
    fun `foreign uri is not a track uri`() {
        assertEquals(null, PlaybackUri.trackId(android.net.Uri.parse("https://example.com/a")))
    }
}
