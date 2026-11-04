package com.facet.music.core.source

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    private val matcher = TrackMatcher()

    private fun track(
        id: String,
        title: String,
        artist: String,
        durationMs: Long = 180_000L,
        isrc: String? = null,
        source: Source = Source.SOUNDCLOUD,
    ) = Track(
        id = id,
        source = source,
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
        val original = track("spotify:1", "Nightcall", "Kavinsky", 258_000L, source = Source.SPOTIFY)
        val candidate = track(
            id = "yt-1",
            title = "Kavinsky - Nightcall (Official Audio)",
            artist = "Kavinsky",
            durationMs = 258_500L,
            source = Source.YOUTUBE,
        )

        assertEquals(candidate, matcher.best(original, listOf(candidate))?.track)
    }

    @Test
    fun `rejects cover version even with matching duration`() {
        val original = track("spotify:1", "Nightcall", "Kavinsky", 258_000L, source = Source.SPOTIFY)
        val cover = track(
            id = "yt-cover",
            title = "Nightcall (Cover by Somebody)",
            artist = "Kavinsky",
            durationMs = 258_000L,
            source = Source.YOUTUBE,
        )

        assertNull(matcher.best(original, listOf(cover)))
    }

    @Test
    fun `rejects candidate outside seven second window`() {
        val original = track("spotify:1", "Nightcall", "Kavinsky", 258_000L, source = Source.SPOTIFY)
        val wrong = track(
            id = "yt-wrong",
            title = "Kavinsky - Nightcall",
            artist = "Kavinsky",
            durationMs = 266_000L,
            source = Source.YOUTUBE,
        )

        assertNull(matcher.best(original, listOf(wrong)))
    }

    @Test
    fun `accepts candidate exactly at seven second border`() {
        val original = track("spotify:1", "Nightcall", "Kavinsky", 258_000L, source = Source.SPOTIFY)
        val candidate = track(
            id = "yt-border",
            title = "Kavinsky - Nightcall",
            artist = "Kavinsky",
            durationMs = 265_000L,
            source = Source.YOUTUBE,
        )

        assertTrue(matcher.match(original, candidate) != null)
    }

    @Test
    fun `accepts artist mentioned in video title`() {
        val original = track("spotify:2", "Ferrari", "Kizaru", 150_000L, source = Source.SPOTIFY)
        val candidate = track(
            id = "yt-2",
            title = "Kizaru - Ferrari (prod. by ...)",
            artist = "Some Channel",
            durationMs = 151_000L,
            source = Source.YOUTUBE,
        )

        assertEquals(candidate, matcher.best(original, listOf(candidate))?.track)
    }

    @Test
    fun `isrc in candidate title adds confidence`() {
        val original = track(
            "spotify:3",
            "Track",
            "Artist",
            200_000L,
            isrc = "FRX282503464",
            source = Source.SPOTIFY,
        )
        val withIsrc = track(
            id = "yt-3",
            title = "Artist - Track FRX282503464",
            artist = "Artist",
            durationMs = 200_000L,
            source = Source.YOUTUBE,
        )
        val without = track(
            id = "yt-4",
            title = "Artist - Track",
            artist = "Artist",
            durationMs = 200_000L,
            source = Source.YOUTUBE,
        )

        assertEquals(withIsrc, matcher.best(original, listOf(without, withIsrc))?.track)
    }

    @Test
    fun `penalizes sped up marker not present in original`() {
        val original = track("spotify:5", "Track", "Artist", 200_000L, source = Source.SPOTIFY)
        val spedUp = track(
            id = "yt-sped",
            title = "Artist - Track (Sped Up)",
            artist = "Artist",
            durationMs = 200_000L,
            source = Source.YOUTUBE,
        )

        assertNull(matcher.best(original, listOf(spedUp)))
    }

    @Test
    fun `keeps live marker when original is live`() {
        val original = track(
            "spotify:6",
            "Track (Live)",
            "Artist",
            200_000L,
            source = Source.SPOTIFY,
        )
        val live = track(
            id = "yt-live",
            title = "Artist - Track (Live)",
            artist = "Artist",
            durationMs = 200_000L,
            source = Source.YOUTUBE,
        )

        assertTrue(matcher.match(original, live) != null)
    }

    @Test
    fun `returns null for empty candidates`() {
        val original = track("spotify:1", "Nightcall", "Kavinsky", source = Source.SPOTIFY)
        assertNull(matcher.best(original, emptyList()))
    }
}
