package com.facet.music.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFormatTest {

    @Test
    fun `fraction is clamped between zero and one`() {
        assertEquals(0f, WidgetFormat.fraction(0L, 0L), 0.0001f)
        assertEquals(0f, WidgetFormat.fraction(-500L, 60_000L), 0.0001f)
        assertEquals(0.5f, WidgetFormat.fraction(30_000L, 60_000L), 0.0001f)
        assertEquals(1f, WidgetFormat.fraction(90_000L, 60_000L), 0.0001f)
    }

    @Test
    fun `time renders minutes and seconds`() {
        assertEquals("0:00", WidgetFormat.time(0L))
        assertEquals("0:07", WidgetFormat.time(7_400L))
        assertEquals("3:45", WidgetFormat.time(225_000L))
        assertEquals("59:59", WidgetFormat.time(3_599_000L))
    }

    @Test
    fun `time renders hours for long tracks`() {
        assertEquals("1:00:00", WidgetFormat.time(3_600_000L))
        assertEquals("2:03:04", WidgetFormat.time(7_384_000L))
    }

    @Test
    fun `snapshot exposes artwork and progress`() {
        val snapshot = WidgetSnapshot(
            trackId = "sc:1",
            title = "Song",
            artToken = "sc:1",
            positionMs = 15_000L,
            durationMs = 60_000L,
        )
        assertEquals(true, snapshot.hasArtwork)
        assertEquals(0.25f, snapshot.progress, 0.0001f)
        assertEquals(false, snapshot.copy(artToken = "").hasArtwork)
        assertEquals(false, snapshot.copy(artToken = "sc:2").hasArtwork)
        assertEquals(false, WidgetSnapshot().hasTrack)
    }
}
