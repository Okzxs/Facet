package com.facet.music.core.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TextPlaylistParserTest {

    @Test
    fun `artist and title are split by space dash space`() {
        val parsed = TextPlaylistParser.parse("ONDA ANDAR - credits song")

        assertEquals(1, parsed.size)
        assertEquals("ONDA ANDAR", parsed.first().artist)
        assertEquals("credits song", parsed.first().title)
    }

    @Test
    fun `line without separator is searched as is`() {
        val parsed = TextPlaylistParser.parse("METAMORPHOSIS")

        assertEquals(1, parsed.size)
        assertNull(parsed.first().artist)
        assertEquals("METAMORPHOSIS", parsed.first().title)
    }

    @Test
    fun `empty lines and duplicates are skipped`() {
        val parsed = TextPlaylistParser.parse(
            """

            ONDA ANDAR - credits song
            onda andar - CREDITS SONG
            METAMORPHOSIS

            """.trimIndent(),
        )

        assertEquals(2, parsed.size)
    }

    @Test
    fun `en dash inside title keeps the first split only`() {
        val parsed = TextPlaylistParser.parse(
            "Rhymes Music - shadowraze & LXNER & quiizzzmeow – adaptive strike",
        )

        assertEquals("Rhymes Music", parsed.single().artist)
        assertTrue(parsed.single().title.endsWith("adaptive strike"))
    }

    @Test
    fun `real soundcloud export parses without failures`() {
        val file = File(
            System.getProperty("user.home") +
                "/Downloads/Telegram Desktop/My Soundcloud Library.txt",
        )
        assumeTrue("личный список недоступен", file.exists())
        val parsed = TextPlaylistParser.parse(file.readText())
        assertTrue("список должен разобраться", parsed.size in 100..500)
        assertTrue(parsed.all { it.title.isNotBlank() })
    }
}
