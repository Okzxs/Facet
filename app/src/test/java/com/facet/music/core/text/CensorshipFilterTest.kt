package com.facet.music.core.text

import com.facet.music.core.search.TrackMerger
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CensorshipFilterTest {

    @After
    fun reset() {
        CensorshipSettings.preferUncensored = true
    }

    @Test
    fun `clean markers are censored`() {
        listOf(
            "Song (Clean)",
            "Song [Clean]",
            "Song - Clean",
            "Song (Clean Version)",
            "Song (Censored)",
            "Song (Bleep)",
            "Песня (цензурная версия)",
            "Песня (без мата)",
        ).forEach { title ->
            assertEquals(title, CensorshipVerdict.CENSORED, CensorshipFilter.verdict(title))
        }
    }

    @Test
    fun `explicit and fan versions are originals`() {
        listOf(
            "Song (Explicit)",
            "Song (Uncensored)",
            "Song (True Version)",
            "Song (OG Version)",
            "Song (Original Version)",
            "Песня (без цензуры)",
        ).forEach { title ->
            assertEquals(title, CensorshipVerdict.ORIGINAL, CensorshipFilter.verdict(title))
        }
    }

    @Test
    fun `artist names do not trigger censorship`() {
        assertEquals(
            CensorshipVerdict.UNKNOWN,
            CensorshipFilter.verdict("Rather Be (Clean Bandit)"),
        )
        assertEquals(
            CensorshipVerdict.UNKNOWN,
            CensorshipFilter.verdict("Symphony (Clean Bandit feat. Zara Larsson)"),
        )
    }

    @Test
    fun `radio edit alone stays unknown`() {
        assertEquals(
            CensorshipVerdict.UNKNOWN,
            CensorshipFilter.verdict("Song (Radio Edit)"),
        )
    }

    @Test
    fun `blank title stays unknown`() {
        assertEquals(CensorshipVerdict.UNKNOWN, CensorshipFilter.verdict(""))
        assertEquals(CensorshipVerdict.UNKNOWN, CensorshipFilter.verdict("   "))
    }

    @Test
    fun `score delta prefers originals and penalizes clean`() {
        assertTrue(CensorshipFilter.scoreDelta("Song (Clean)") < 0)
        assertTrue(CensorshipFilter.scoreDelta("Song (Explicit)") > 0)
        assertEquals(0.0, CensorshipFilter.scoreDelta("Song"), 0.0001)
    }

    @Test
    fun `score delta respects the preference flag`() {
        CensorshipSettings.preferUncensored = false
        assertEquals(0.0, CensorshipFilter.scoreDelta("Song (Clean)"), 0.0001)
        assertEquals(0.0, CensorshipFilter.scoreDelta("Song (Explicit)"), 0.0001)
    }

    @Test
    fun `merger puts the original above the clean version`() {
        val clean = track("1", "Song (Clean)", Source.SOUNDCLOUD)
        val original = track("2", "Song (Explicit)", Source.SOUNDCLOUD)

        val merged = TrackMerger.merge(listOf(listOf(clean), listOf(original)), "song")

        assertEquals("2", merged.first().id)
    }

    private fun track(id: String, title: String, source: Source) = Track(
        id = id,
        source = source,
        title = title,
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
    )
}
