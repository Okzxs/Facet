package com.facet.music.core.network

import com.facet.music.core.search.TrackMerger
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMergerOrderTest {

    @Test
    fun `новые источники добавляются в конец без перестановки`() {
        val first = track("Artist", "One")
        val second = track("Artist", "Two")

        val shown = TrackMerger.merge(listOf(listOf(first)))
        val merged = TrackMerger.mergeKeepingOrder(
            existing = shown,
            groups = listOf(listOf(first), listOf(second)),
        )

        assertEquals(listOf(first.id, second.id), merged.map { it.id })
    }

    @Test
    fun `полный поток важнее SoundCloud Go+`() {
        val goPlus = track("Artist", "One").copy(isSubscriptionOnly = true)
        val youtube = track("Artist", "One").copy(
            id = "youtube-one",
            source = Source.YOUTUBE,
            transcodingUrl = "https://youtube.com/watch?v=one",
        )

        val merged = TrackMerger.merge(listOf(listOf(goPlus), listOf(youtube)))

        assertEquals(Source.YOUTUBE, merged.single().source)
        assertEquals(false, merged.single().isSubscriptionOnly)
        assertEquals(2, merged.single().sourceCount)
    }
}

private fun track(artist: String, title: String): Track = Track(
    id = "$artist-$title",
    source = Source.SOUNDCLOUD,
    title = title,
    artist = artist,
    artworkUrl = null,
    streamUrl = null,
    durationMs = 180_000,
    genre = null,
    transcodingUrl = "https://soundcloud.com/$artist/$title",
)
