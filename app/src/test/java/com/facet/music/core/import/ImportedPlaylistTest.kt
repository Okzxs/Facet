package com.facet.music.core.import

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedPlaylistTest {
    private val track = Track(
        id = "track", source = Source.YOUTUBE, title = "Track", artist = "Artist",
        streamUrl = null, durationMs = 1_000L, genre = null, transcodingUrl = "url",
    )

    @Test fun `imported playlist defaults to complete`() {
        assertFalse(ImportedPlaylist("name", ImportService.YOUTUBE, listOf(track)).isIncomplete)
    }

    @Test fun `imported playlist can report truncation`() {
        assertTrue(ImportedPlaylist("name", ImportService.YOUTUBE, listOf(track), true).isIncomplete)
    }
}
