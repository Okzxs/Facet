package com.my.music.data.source.wave

import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LastFmRepository
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.TasteRepository
import com.my.music.data.repository.TasteWeight
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveRepositoryTest {

    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()
    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val chartsRepository = mockk<SoundCloudChartsRepository>()
    private val lastFmRepository = mockk<LastFmRepository>()
    private val tasteRepository = mockk<TasteRepository>()

    private lateinit var repository: WaveRepositoryImpl

    @Before
    fun setUp() {
        every { likedTracksRepository.getLikedTracks() } returns flowOf(emptyList())
        every { playHistoryRepository.getPlayHistory() } returns flowOf(emptyList())
        coEvery { soundCloudRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { chartsRepository.getTop50() } returns Result.success(emptyList())
        coEvery { lastFmRepository.getSimilarArtists(any(), any()) } returns
            Result.success(emptyList())
        coEvery { tasteRepository.getTopArtists(any()) } returns emptyList()
        coEvery { tasteRepository.getTopGenres(any()) } returns emptyList()
        coEvery { tasteRepository.isRussianPriority() } returns false

        repository = WaveRepositoryImpl(
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            soundCloudRepository = soundCloudRepository,
            chartsRepository = chartsRepository,
            lastFmRepository = lastFmRepository,
            tasteRepository = tasteRepository,
        )
    }

    @Test
    fun `combines all sources without duplicates and updates session counters`() = runBlocking {
        val liked = (1..12).map { track("l$it", "Rock") }
        val history = (1..5).map { track("h$it", null) }

        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        every { playHistoryRepository.getPlayHistory() } returns flowOf(history)
        coEvery { tasteRepository.getTopArtists(any()) } returns
            listOf(TasteWeight("Artist", 10.0))
        coEvery { lastFmRepository.getSimilarArtists("Artist", 4) } returns
            Result.success(listOf("Similar A", "Similar B"))
        coEvery { soundCloudRepository.search("Similar A", 5) } returns
            Result.success((1..5).map { track("sa$it", null) })
        coEvery { soundCloudRepository.search("Similar B", 5) } returns
            Result.success((1..5).map { track("sb$it", null) })
        coEvery { soundCloudRepository.search("Rock", 6) } returns
            Result.success((1..5).map { track("g$it", "Rock") })

        val result = repository.generateWave().getOrThrow()

        assertEquals(20, result.tracks.size)
        assertEquals(20, result.tracks.distinctBy { it.id }.size)
        assertEquals(10, result.fromLikes)
        assertEquals(4, result.fromHistory)
        assertEquals(2, result.fromGenres)
        assertEquals(4, result.fromSimilar)
        assertEquals(result.tracks.size, result.fromLikes + result.fromHistory +
            result.fromGenres + result.fromSimilar)

        val session = repository.activeSession.value
        assertEquals(20, session?.shownTrackIds?.size)
        assertEquals(1, session?.batchNumber)
        assertFalse(session?.isTerminated ?: true)
    }

    @Test
    fun `next batch skips shown tracks and terminates when sources are exhausted`() = runBlocking {
        val liked = (1..5).map { track("l$it", null) }
        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        coEvery { chartsRepository.getTop50() } returns Result.success(
            liked + (1..5).map { track("c$it", null) },
        )

        repository.startSession().getOrThrow()

        val first = repository.nextBatch(size = 5).getOrThrow()
        assertEquals(liked.map { it.id }.sorted(), first.tracks.map { it.id }.sorted())
        assertEquals(5, first.session.fromLikes)

        val second = repository.nextBatch(size = 5).getOrThrow()
        assertEquals((1..5).map { "c$it" }.sorted(), second.tracks.map { it.id }.sorted())
        assertEquals(5, second.session.fromGenres)

        assertTrue(first.tracks.map { it.id }.intersect(second.tracks.map { it.id }.toSet()).isEmpty())

        val third = repository.nextBatch(size = 5).getOrThrow()
        assertTrue(third.tracks.isEmpty())
        assertTrue(third.session.isTerminated)
        assertEquals(10, third.session.shownTrackIds.size)
        assertEquals(3, third.session.batchNumber)
    }

    @Test
    fun `russian priority puts cyrillic tracks first`() = runBlocking {
        coEvery { tasteRepository.isRussianPriority() } returns true
        coEvery { chartsRepository.getTop50() } returns Result.success(
            listOf(
                track("e1", null, title = "English one"),
                track("e2", null, title = "English two"),
                track("r1", null, title = "Русский трек"),
                track("e3", null, title = "English three"),
                track("r2", null, title = "Ещё один трек"),
            ),
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(size = 5).getOrThrow()

        assertEquals(5, batch.tracks.size)
        val cyrillic = '\u0400'..'\u04FF'
        assertTrue(batch.tracks.take(2).all { it.title.any { char -> char in cyrillic } })
        assertTrue(batch.tracks.drop(2).none { it.title.any { char -> char in cyrillic } })
    }

    @Test
    fun `empty sources yield an empty terminated wave`() = runBlocking {
        repository.startSession().getOrThrow()

        val batch = repository.nextBatch(size = 5).getOrThrow()

        assertTrue(batch.tracks.isEmpty())
        assertTrue(batch.session.isTerminated)
        assertTrue(repository.activeSession.value?.isTerminated ?: false)
    }

    private fun track(
        id: String,
        genre: String?,
        title: String = "Title $id",
    ) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = genre,
        transcodingUrl = "https://soundcloud.com/test/$id",
    )
}
