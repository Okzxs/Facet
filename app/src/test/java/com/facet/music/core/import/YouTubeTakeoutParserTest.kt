package com.facet.music.core.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTakeoutParserTest {

    @Test
    fun `parses liked music csv with artist and title`() {
        val csv = """
            Video ID,Timestamp,Title
            abc123,2026-11-20T10:00:00Z,Daft Punk - One More Time
            def456,2026-11-21T11:00:00Z,Justice - D.A.N.C.E.
        """.trimIndent()

        val tracks = YouTubeTakeoutParser.parse(csv)

        assertEquals(2, tracks.size)
        assertEquals("Daft Punk", tracks[0].artist)
        assertEquals("One More Time", tracks[0].title)
        assertEquals("abc123", tracks[0].videoId)
    }

    @Test
    fun `deduplicates and keeps quoted commas`() {
        val csv = """
            "Video ID","Title"
            "x1","Artist - Song, with comma"
            "x1","Artist - Song, with comma"
        """.trimIndent()

        val tracks = YouTubeTakeoutParser.parse(csv)

        assertEquals(1, tracks.size)
        assertEquals("Song, with comma", tracks[0].title)
    }

    @Test
    fun `title without separator becomes title only`() {
        val tracks = YouTubeTakeoutParser.parse("Video ID,Title\nq1,Just a song")

        assertEquals(1, tracks.size)
        assertEquals("Just a song", tracks[0].title)
        assertTrue(tracks[0].artist.isBlank())
    }

    @Test
    fun `returns empty for unrelated csv`() {
        assertTrue(YouTubeTakeoutParser.parse("a,b\n1,2").isEmpty())
        assertTrue(YouTubeTakeoutParser.parse("").isEmpty())
    }
}
