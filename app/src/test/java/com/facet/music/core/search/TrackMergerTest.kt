package com.facet.music.core.search

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Сводный поиск: одинаковые треки из разных источников схлопываются в один,
 * счётчик источников растёт, приоритет — у полного потока, а не превью.
 */
class TrackMergerTest {

    private fun track(
        id: String,
        source: Source,
        title: String,
        artist: String,
        streamUrl: String? = null,
        previewSeconds: Int? = null,
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = streamUrl,
        durationMs = 180_000,
        genre = null,
        previewSeconds = previewSeconds,
    )

    @Test
    fun `same track from two sources becomes one with count two`() {
        val merged = TrackMerger.merge(
            listOf(
                listOf(track("sc:1", Source.SOUNDCLOUD, "Song", "Artist", streamUrl = "https://sc")),
                listOf(
                    track(
                        "dz:1",
                        Source.DEEZER,
                        "Song",
                        "Artist",
                        streamUrl = "https://preview",
                        previewSeconds = 30,
                    ),
                ),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals(2, merged.first().sourceCount)
        assertEquals(Source.SOUNDCLOUD, merged.first().source)
    }

    @Test
    fun `preview is not preferred over full stream`() {
        val merged = TrackMerger.merge(
            listOf(
                listOf(track("dz:1", Source.DEEZER, "Song", "Artist", "https://p", 30)),
                listOf(track("yt:1", Source.YOUTUBE, "Song", "Artist", "https://full")),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals(Source.YOUTUBE, merged.first().source)
    }

    @Test
    fun `brackets and case do not create duplicates`() {
        val merged = TrackMerger.merge(
            listOf(
                listOf(track("sc:1", Source.SOUNDCLOUD, "Song (Remix)", "ARTIST", "https://sc")),
                listOf(track("yt:1", Source.YOUTUBE, "song", "artist", "https://yt")),
            ),
        )

        assertEquals(1, merged.size)
        assertEquals(2, merged.first().sourceCount)
    }

    @Test
    fun `different artists stay separate`() {
        val merged = TrackMerger.merge(
            listOf(
                listOf(
                    track("sc:1", Source.SOUNDCLOUD, "Song", "Artist One", "https://a"),
                    track("sc:2", Source.SOUNDCLOUD, "Song", "Artist Two", "https://b"),
                ),
            ),
        )

        assertEquals(2, merged.size)
        assertEquals(listOf(1, 1), merged.map { it.sourceCount })
    }

    @Test
    fun `blank tracks are skipped`() {
        val merged = TrackMerger.merge(
            listOf(
                listOf(
                    track("", Source.SOUNDCLOUD, "Song", "Artist", "https://a"),
                    track("sc:2", Source.SOUNDCLOUD, "", "Artist", "https://b"),
                ),
            ),
        )

        assertEquals(0, merged.size)
    }
}
