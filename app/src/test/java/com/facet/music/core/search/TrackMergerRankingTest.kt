package com.facet.music.core.search

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMergerRankingTest {

    @Test
    fun `точный артист важнее лишнего источника`() {
        val exact = track("exact", "Artist", "Song", Source.SOUNDCLOUD)
        val cover = track("cover-1", "Someone", "Artist cover", Source.SOUNDCLOUD)
        val cover2 = track("cover-2", "Someone", "Artist cover", Source.YOUTUBE)

        val merged = TrackMerger.merge(
            listOf(listOf(exact), listOf(cover, cover2)),
            query = "Artist",
        )

        assertEquals("exact", merged.first().id)
    }

    @Test
    fun `версия получает штраф, если слова нет в запросе`() {
        val original = track("original", "Artist", "Song", Source.SOUNDCLOUD)
        val remix = track("remix", "Artist", "Song (Nightcore Remix)", Source.YOUTUBE)

        val merged = TrackMerger.merge(listOf(listOf(remix), listOf(original)), query = "Song")

        assertEquals("original", merged.first().id)
    }

    @Test
    fun `позиция в выдаче источника влияет на счёт`() {
        val first = track("first", "Artist", "Song", Source.SOUNDCLOUD)
        val second = track("second", "Artist", "Other", Source.SOUNDCLOUD)

        val merged = TrackMerger.merge(listOf(listOf(first, second)), query = "Song")

        assertEquals("first", merged.first().id)
    }

    private fun track(id: String, artist: String, title: String, source: Source): Track =
        Track(
            id = id,
            source = source,
            title = title,
            artist = artist,
            artworkUrl = null,
            streamUrl = null,
            durationMs = 180_000,
            genre = null,
        )
}
