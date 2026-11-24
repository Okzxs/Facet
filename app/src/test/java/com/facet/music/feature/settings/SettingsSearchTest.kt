package com.facet.music.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val entries = settingsSearchEntries()

    private fun matches(entry: SettingsSearchEntry, query: String): Boolean =
        entry.matches(query = query, title = "Заголовок", hint = "Подсказка")

    @Test
    fun `every entry has keywords and an anchor`() {
        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            assertTrue(entry.keywords.isNotEmpty())
            assertTrue(entry.anchor.isNotBlank())
        }
    }

    @Test
    fun `blank query matches nothing`() {
        assertFalse(matches(entries.first(), ""))
        assertFalse(matches(entries.first(), "   "))
    }

    @Test
    fun `latin synonyms find spotify`() {
        val spotify = entries.first { it.keywords.contains("spotify") }
        assertTrue(matches(spotify, "spotify"))
        assertTrue(matches(spotify, "client"))
    }

    @Test
    fun `cyrillic synonyms find spotify and cache`() {
        val spotify = entries.first { it.keywords.contains("спотифай") }
        assertTrue(matches(spotify, "спотифай"))

        val cache = entries.first { it.keywords.contains("кэш") }
        assertTrue(matches(cache, "кэш"))
        assertTrue(matches(cache, "очистк"))
    }

    @Test
    fun `queries match localized title and hint`() {
        val entry = entries.first()
        assertTrue(entry.matches(query = "заголовок", title = "Заголовок", hint = ""))
        assertTrue(entry.matches(query = "подсказка", title = "", hint = "Подсказка"))
    }

    @Test
    fun `unknown query matches nothing`() {
        assertTrue(entries.none { matches(it, "qwerty-unknown") })
    }

    @Test
    fun `studio entry opens the studio and points to appearance`() {
        val studio = entries.first { it.opensStudio }
        assertEquals(ANCHOR_APPEARANCE, studio.anchor)
        assertEquals(1, entries.count { it.opensStudio })
    }

    @Test
    fun `every entry has a distinct title`() {
        assertEquals(entries.size, entries.map { it.titleRes }.distinct().size)
    }
}
