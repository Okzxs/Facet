package com.facet.music.core.source

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Скоринг матчинга YouTube: главное — не подставить чужой трек.
 */
class YouTubeMatcherTest {

    private val matcher = YouTubeMatcher()

    private fun track(
        id: String,
        title: String,
        artist: String,
        durationMs: Long = 180_000L,
        isrc: String? = null,
    ) = Track(
        id = id,
        source = Source.YOUTUBE,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = null,
        isrc = isrc,
    )

    @Test
    fun `picks studio version with matching artist and duration`() {
        val original = track("deezer:1", "Nightcall", "Kavinsky", 258_000L)
        val candidate = track(
            id = "yt-1",
            title = "Kavinsky - Nightcall (Official Audio)",
            artist = "Kavinsky",
            durationMs = 258_500L,
        )

        assertEquals(candidate, matcher.best(original, listOf(candidate)))
    }

    @Test
    fun `rejects cover version even with matching duration`() {
        val original = track("deezer:1", "Nightcall", "Kavinsky", 258_000L)
        val cover = track(
            id = "yt-cover",
            title = "Nightcall (Cover by Somebody)",
            artist = "Kavinsky",
            durationMs = 258_000L,
        )

        assertNull(matcher.best(original, listOf(cover)))
    }

    @Test
    fun `rejects candidate with very different duration`() {
        val original = track("deezer:1", "Nightcall", "Kavinsky", 258_000L)
        val wrong = track(
            id = "yt-wrong",
            title = "Kavinsky - Nightcall",
            artist = "Kavinsky",
            durationMs = 600_000L,
        )

        assertNull(matcher.best(original, listOf(wrong)))
    }

    @Test
    fun `accepts artist mentioned in video title`() {
        val original = track("apple:2", "Ferrari", "Kizaru", 150_000L)
        val candidate = track(
            id = "yt-2",
            title = "Kizaru - Ferrari (prod. by ...)",
            artist = "Some Channel",
            durationMs = 151_000L,
        )

        assertEquals(candidate, matcher.best(original, listOf(candidate)))
    }

    @Test
    fun `isrc in candidate title adds confidence`() {
        val original = track("deezer:3", "Track", "Artist", 200_000L, isrc = "FRX282503464")
        val withIsrc = track(
            id = "yt-3",
            title = "Artist - Track FRX282503464",
            artist = "Artist",
            durationMs = 200_000L,
        )
        val without = track(
            id = "yt-4",
            title = "Artist - Track",
            artist = "Artist",
            durationMs = 200_000L,
        )

        assertEquals(withIsrc, matcher.best(original, listOf(without, withIsrc)))
    }

    @Test
    fun `returns null for empty candidates`() {
        val original = track("deezer:1", "Nightcall", "Kavinsky")
        assertNull(matcher.best(original, emptyList()))
    }
}
