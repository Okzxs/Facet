package com.facet.music.core.lyrics

import com.facet.music.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAutoTimerTest {

    @Test
    fun `lines are spread across the track with increasing times`() {
        val lines = listOf(
            LyricLine(0, "short"),
            LyricLine(0, "a much longer line here"),
            LyricLine(0, "medium line"),
        )

        val timed = LyricsAutoTimer.distribute(lines, 200_000L)

        assertEquals(3, timed.size)
        assertTrue(timed[0].timeMs >= 9_000L)
        assertTrue(timed[1].timeMs > timed[0].timeMs)
        assertTrue(timed[2].timeMs > timed[1].timeMs)
        assertTrue(timed.last().timeMs < 200_000L)
    }

    @Test
    fun `longer lines get more time than short ones`() {
        val lines = listOf(
            LyricLine(0, "a"),
            LyricLine(0, "a very long line with many words inside"),
            LyricLine(0, "b"),
        )

        val timed = LyricsAutoTimer.distribute(lines, 100_000L)

        val firstGap = timed[1].timeMs - timed[0].timeMs
        val secondGap = timed[2].timeMs - timed[1].timeMs
        assertTrue(secondGap > firstGap)
    }

    @Test
    fun `gap lines keep their place`() {
        val lines = listOf(
            LyricLine(0, "one"),
            LyricLine(0, "", alignment = com.facet.music.data.model.LyricAlignment.Start),
            LyricLine(0, "two"),
        )

        val timed = LyricsAutoTimer.distribute(lines, 60_000L)

        assertTrue(timed[1].isGap)
        assertTrue(timed[2].timeMs > timed[0].timeMs)
    }

    @Test
    fun `zero duration leaves lyrics unchanged`() {
        val lines = listOf(LyricLine(5_000L, "one"))

        assertEquals(lines, LyricsAutoTimer.distribute(lines, 0L))
    }
}
