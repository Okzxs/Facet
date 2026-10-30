package com.facet.music.core.player

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/** Чистая логика «играть следующим»: вставка и перенос трека в очереди. */
class QueueInsertionTest {

    private val queue = listOf(track("one"), track("two"), track("three"))

    @Test
    fun `inserts new track right after current`() {
        val result = insertNextTrack(queue, currentIndex = 0, track = track("next"))

        assertEquals(listOf("one", "next", "two", "three"), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `moves track from the end of the queue`() {
        val result = insertNextTrack(queue, currentIndex = 1, track = track("three"))

        assertEquals(listOf("one", "two", "three"), result.queue.map { it.id })
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `moves track from before current and shifts index`() {
        val result = insertNextTrack(queue, currentIndex = 2, track = track("one"))

        assertEquals(listOf("two", "three", "one"), result.queue.map { it.id })
        assertEquals(1, result.currentIndex)
        assertEquals("three", result.queue[result.currentIndex].id)
    }

    @Test
    fun `keeps queue when requested track is already playing`() {
        val result = insertNextTrack(queue, currentIndex = 1, track = track("two"))

        assertEquals(queue, result.queue)
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `keeps queue when index is out of range`() {
        val result = insertNextTrack(queue, currentIndex = -1, track = track("next"))

        assertEquals(queue, result.queue)
        assertEquals(-1, result.currentIndex)
    }

    @Test
    fun `ignores track without id`() {
        val result = insertNextTrack(queue, currentIndex = 0, track = track(""))

        assertEquals(queue, result.queue)
        assertEquals(0, result.currentIndex)
    }

    private fun track(id: String) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = null,
    )
}
