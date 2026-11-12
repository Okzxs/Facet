package com.facet.music.feature.player

import com.facet.music.data.model.LyricLine
import com.facet.music.data.model.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLyricsTest {

    private val sample = LyricsData(
        lines = listOf("Line 1", "Line 2", "Line 3"),
        timesMs = longArrayOf(1_000L, 5_000L, 10_000L),
        isSynced = true,
        isInstrumental = false,
    )

    @Test
    fun `activeIndexAt returns -1 before first line accounting for lead`() {
        assertEquals(-1, sample.activeIndexAt(0L))
        assertEquals(-1, sample.activeIndexAt(799L))
        assertEquals(0, sample.activeIndexAt(800L))
    }

    @Test
    fun `activeIndexAt matches exact timestamp, between timestamps, and after last`() {
        assertEquals(0, sample.activeIndexAt(1_000L))
        assertEquals(0, sample.activeIndexAt(3_500L))
        assertEquals(1, sample.activeIndexAt(5_000L))
        assertEquals(1, sample.activeIndexAt(7_500L))
        assertEquals(2, sample.activeIndexAt(10_000L))
        assertEquals(2, sample.activeIndexAt(120_000L))
    }

    @Test
    fun `activeIndexAt returns -1 when unsynced or empty`() {
        val unsynced = sample.copy(isSynced = false)
        val empty = sample.copy(lines = emptyList(), timesMs = LongArray(0))

        assertEquals(-1, unsynced.activeIndexAt(5_000L))
        assertEquals(-1, empty.activeIndexAt(5_000L))
    }

    @Test
    fun `progressAt interpolates smoothly within line and clamps to 0 to 1`() {
        assertEquals(0f, sample.progressAt(0, 500L), 0.001f)
        assertEquals(0.5f, sample.progressAt(0, 3_000L), 0.001f)
        assertEquals(1f, sample.progressAt(0, 6_000L), 0.001f)
        assertEquals(0.5f, sample.progressAt(2, 12_500L), 0.001f)
        assertEquals(0f, sample.progressAt(-1, 1_000L), 0.001f)
    }

    @Test
    fun `buildLyricsData sorts lines and replaces blank text with musical note`() {
        val data = buildLyricsData(
            LyricsResult(
                lines = listOf(
                    LyricLine(5_000L, "Second"),
                    LyricLine(1_000L, ""),
                ),
                isSynced = true,
            ),
        )

        assertEquals(listOf("♪", "Second"), data.lines)
        assertEquals(listOf(1_000L, 5_000L), data.timesMs.toList())
    }

    @Test
    fun `playbackClock interpolates position when playing and freezes on pause`() {
        val clock = PlaybackClock()
        clock.speed = 1f
        clock.playing = true
        clock.resync(positionMs = 10_000L, frameMs = 1_000L)

        clock.frameMs = 1_250L
        assertEquals(10_250L, clock.nowMs())

        clock.speed = 1.5f
        clock.frameMs = 1_400L
        assertEquals(10_600L, clock.nowMs())

        clock.playing = false
        clock.baseMs = 10_600L
        clock.frameMs = 5_000L
        assertEquals(10_600L, clock.nowMs())
    }
}
