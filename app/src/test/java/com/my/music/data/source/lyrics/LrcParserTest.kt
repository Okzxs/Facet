package com.my.music.data.source.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses hundredths tenths and milliseconds and sorts by time`() {
        val result = LrcParser.parse(
            """
            [00:12.34]First
            [01:02.5]Second
            [00:03.007]Third
            """.trimIndent(),
        )

        assertNotNull(result)
        result!!
        assertTrue(result.isSynced)
        assertEquals(listOf(3_007L, 12_340L, 62_500L), result.lines.map { it.timeMs })
        assertEquals(listOf("Third", "First", "Second"), result.lines.map { it.text })
    }

    @Test
    fun `applies multiple timestamps of one line`() {
        val result = LrcParser.parse("[00:10.00][00:05.00]Chorus")

        assertNotNull(result)
        result!!
        assertEquals(listOf(5_000L, 10_000L), result.lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Chorus"), result.lines.map { it.text })
    }

    @Test
    fun `applies positive offset by shifting lines earlier`() {
        val result = LrcParser.parse(
            """
            [offset:+500]
            [00:10.00]A
            [00:01.00]B
            """.trimIndent(),
        )

        assertNotNull(result)
        result!!
        assertEquals(listOf(500L, 9_500L), result.lines.map { it.timeMs })
    }

    @Test
    fun `applies negative offset by shifting lines later`() {
        val result = LrcParser.parse(
            """
            [offset:-250]
            [00:01.00]A
            """.trimIndent(),
        )

        assertNotNull(result)
        result!!
        assertEquals(1_250L, result.lines.single().timeMs)
    }

    @Test
    fun `clamps negative time to zero after offset`() {
        val result = LrcParser.parse(
            """
            [offset:5000]
            [00:01.00]A
            """.trimIndent(),
        )

        assertNotNull(result)
        result!!
        assertEquals(0L, result.lines.single().timeMs)
    }

    @Test
    fun `strips karaoke word timing tags`() {
        val result = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:02.00>world")

        assertNotNull(result)
        result!!
        assertEquals("Hello world", result.lines.single().text)
    }

    @Test
    fun `falls back to plain text when lrc has no timed lines`() {
        val result = LrcParser.parse("[ar:Artist]\n[ti:Title]", "First\nSecond")

        assertNotNull(result)
        result!!
        assertFalse(result.isSynced)
        assertEquals(listOf("First", "Second"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.timeMs == -1L })
    }

    @Test
    fun `parses unsynchronized text preserving inner blank line`() {
        val result = LrcParser.parse(null, "Line one\n\nLine two\n\n")

        assertNotNull(result)
        result!!
        assertFalse(result.isSynced)
        assertEquals(listOf("Line one", "", "Line two"), result.lines.map { it.text })
        assertTrue(result.lines.all { it.timeMs == -1L })
    }

    @Test
    fun `handles crlf and skips untimed rows`() {
        val result = LrcParser.parse("[00:01.00]Hi\r\n\r\n[00:02.00]Bye")

        assertNotNull(result)
        result!!
        assertEquals(listOf("Hi", "Bye"), result.lines.map { it.text })
    }

    @Test
    fun `returns null for empty input and metadata only`() {
        assertNull(LrcParser.parse("", null))
        assertNull(LrcParser.parse(null, null))
        assertNull(LrcParser.parse("   ", null))
        assertNull(LrcParser.parse("[ar:X]", null))
    }
}
