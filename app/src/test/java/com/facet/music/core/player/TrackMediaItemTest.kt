package com.facet.music.core.player

import androidx.media3.common.MimeTypes
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackMediaItemTest {

    @Test
    fun `media item carries full track in metadata extras`() {
        val track = Track(
            id = "https://soundcloud.com/artist/track",
            source = Source.SOUNDCLOUD,
            title = "Track",
            artist = "Artist",
            artworkUrl = "https://img.example/cover.jpg",
            streamUrl = null,
            durationMs = 180_000L,
            genre = "Electronic",
            transcodingUrl = "https://soundcloud.com/artist/track",
            isrc = "ABC123",
        )

        val item = TrackMediaItem.create(track, MimeTypes.AUDIO_MPEG)

        assertEquals(track, TrackMediaItem.trackOf(item))
        assertEquals(track.id, item.mediaId)
        assertEquals(track.id, item.localConfiguration?.customCacheKey)
        assertEquals("Electronic", item.mediaMetadata.albumTitle?.toString())
        assertEquals(180_000L, item.mediaMetadata.durationMs)
    }
}
