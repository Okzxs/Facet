package com.facet.music.core.search

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {

    private fun track(id: String, title: String, artist: String) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000,
        genre = null,
    )

    private val catalog = listOf(
        track("1", "Get Lucky", "Daft Punk"),
        track("2", "Lucky Strike", "Maroon 5"),
        track("3", "Ночное кафе", "ONDA ANDAR"),
        track("4", "Get Lucky (Remix)", "Daft Punk"),
        track("5", "Lucky", "Daft Punk"),
    )

    @Test
    fun `short query returns nothing`() {
        assertTrue(LocalSearch.match(catalog, "").isEmpty())
        assertTrue(LocalSearch.match(catalog, "g").isEmpty())
        assertTrue(LocalSearch.match(catalog, "  ").isEmpty())
    }

    @Test
    fun `matches title case insensitively`() {
        val result = LocalSearch.match(catalog, "lucky strike")
        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun `matches artist and keeps title ranking`() {
        val result = LocalSearch.match(catalog, "daft")
        assertEquals(setOf("1", "4", "5"), result.map { it.id }.toSet())
    }

    @Test
    fun `title prefix ranks above title contain`() {
        val result = LocalSearch.match(catalog, "lucky")
        assertEquals("5", result.first().id)
    }

    @Test
    fun `tokens can be out of order`() {
        val result = LocalSearch.match(catalog, "punk lucky")
        assertTrue(result.any { it.id == "5" || it.id == "1" })
    }

    @Test
    fun `cyrillic query finds latin artist through transliteration`() {
        val result = LocalSearch.match(catalog, "дафт")
        assertEquals(setOf("1", "4", "5"), result.map { it.id }.toSet())
    }

    @Test
    fun `cyrillic query matches cyrillic title`() {
        val result = LocalSearch.match(catalog, "ночное")
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test
    fun `limit is respected`() {
        assertEquals(2, LocalSearch.match(catalog, "lucky", limit = 2).size)
    }

    @Test
    fun `duplicate ids are collapsed`() {
        val doubled = catalog + catalog
        val result = LocalSearch.match(doubled, "lucky")
        assertEquals(result.map { it.id }.distinct().size, result.size)
    }

    @Test
    fun `unknown query returns nothing`() {
        assertTrue(LocalSearch.match(catalog, "zzzz").isEmpty())
    }
}
