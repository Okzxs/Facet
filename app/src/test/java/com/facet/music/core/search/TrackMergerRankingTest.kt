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

    @Test
    fun `присутствие в нескольких источниках повышает ранг`() {
        val single = track("single", "Artist A", "Alpha", Source.YOUTUBE)
        val dualSc = track("dual-sc", "Artist B", "Beta", Source.SOUNDCLOUD)
        val dualYt = track("dual-yt", "Artist B", "Beta", Source.YOUTUBE)

        val merged = TrackMerger.merge(
            listOf(
                listOf(single),
                listOf(dualSc),
                listOf(dualYt),
            ),
            query = "something",
        )

        assertEquals("dual-sc", merged.first().id)
        assertEquals(2, merged.first().sourceCount)
    }

    @Test
    fun `слово версии в запросе отменяет штраф за версию`() {
        val plain = track("plain", "Artist", "Night Song", Source.SOUNDCLOUD)
        val remix = track("remix", "Artist", "Day Song (Club Remix)", Source.YOUTUBE)

        val withoutKeyword = TrackMerger.merge(
            listOf(listOf(plain), listOf(remix)),
            query = "Song",
        )
        assertEquals("plain", withoutKeyword.first().id)

        val withKeyword = TrackMerger.merge(
            listOf(listOf(plain), listOf(remix)),
            query = "Day Song Remix",
        )
        assertEquals("remix", withKeyword.first().id)
    }

    @Test
    fun `совпадение всех токенов запроса поднимает трек`() {
        val partial = track("partial", "Artist", "Midnight", Source.SOUNDCLOUD)
        val full = track("full", "Artist", "Midnight City", Source.YOUTUBE)

        val merged = TrackMerger.merge(
            listOf(listOf(partial), listOf(full)),
            query = "Artist Midnight City",
        )

        assertEquals("full", merged.first().id)
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
