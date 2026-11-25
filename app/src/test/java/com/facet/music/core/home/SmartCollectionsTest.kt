package com.facet.music.core.home

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCollectionsTest {

    private fun track(id: String, title: String = "Track $id", artist: String = "Artist") = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
    )

    private val liked = listOf(
        track("1", artist = "A"),
        track("2", artist = "B"),
        track("3", artist = "C"),
        track("4", artist = "D"),
    )

    @Test
    fun `forgotten drops recently played tracks`() {
        val result = SmartCollections.forgotten(liked, setOf("1", "3"))
        assertEquals(listOf("2", "4"), result.map { it.id })
    }

    @Test
    fun `forgotten keeps liked order`() {
        val result = SmartCollections.forgotten(liked, emptySet())
        assertEquals(listOf("1", "2", "3", "4"), result.map { it.id })
    }

    @Test
    fun `forgotten respects limit`() {
        assertEquals(2, SmartCollections.forgotten(liked, emptySet(), limit = 2).size)
    }

    @Test
    fun `everything played leaves forgotten empty`() {
        assertTrue(SmartCollections.forgotten(liked, liked.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `forgotten skips excluded fresh likes`() {
        val result = SmartCollections.forgotten(liked, emptySet(), excludeIds = setOf("1", "2"))
        assertEquals(listOf("3", "4"), result.map { it.id })
    }

    @Test
    fun `collections show one track per artist`() {
        val sameArtist = listOf(
            track("1", artist = "ONDA ANDAR"),
            track("2", artist = "onda andar"),
            track("3", artist = "Surf7"),
        )
        assertEquals(listOf("1", "3"), SmartCollections.freshLikes(sameArtist).map { it.id })
        assertEquals(listOf("1", "3"), SmartCollections.forgotten(sameArtist, emptySet()).map { it.id })
    }

    @Test
    fun `fresh likes keep newest first`() {
        val result = SmartCollections.freshLikes(liked, limit = 2)
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun `empty library gives empty collections`() {
        assertTrue(SmartCollections.forgotten(emptyList(), emptySet()).isEmpty())
        assertTrue(SmartCollections.freshLikes(emptyList()).isEmpty())
    }
}
