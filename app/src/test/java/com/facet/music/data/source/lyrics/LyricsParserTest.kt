package com.facet.music.data.source.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun `ttml keeps syllables and the line timing`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="1.000" end="2.500">
                  <span begin="1.000" end="1.500">Hel</span><span begin="1.500" end="2.500">lo</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val lines = TtmlParser.parse(ttml)

        assertEquals(1, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(2_500L, lines[0].sungUntilMs)
        assertEquals(1, lines[0].words.size)
        assertEquals(2, lines[0].words[0].syllables.size)
    }

    @Test
    fun `enhanced lrc splits words and follows their stamps`() {
        val lrc = "[00:05.00]<00:05.00>Hello <00:06.00>world"

        val lines = EnhancedLrcParser.parse(lrc)

        assertEquals(1, lines.size)
        assertEquals(listOf("Hello", "world"), lines[0].words.map { it.text })
        assertEquals(5_000L, lines[0].words[0].startMs)
        assertEquals(6_000L, lines[0].words[1].startMs)
        assertTrue(lines[0].revealedChars(5_500L) > 0f)
    }

    @Test
    fun `provider envelope is unwrapped before parsing`() {
        val raw = """{"ttml":"<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p begin=\"2.000\">One line</p></div></body></tt>"}"""

        val lines = ProviderLyrics.parse(raw)

        assertEquals("One line", lines?.single()?.text)
        assertEquals(2_000L, lines?.single()?.timeMs)
    }
}
