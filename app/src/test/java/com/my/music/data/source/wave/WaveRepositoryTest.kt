package com.my.music.data.source.wave

import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.data.repository.SoundCloudRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveRepositoryTest {

    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()
    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val chartsRepository = mockk<SoundCloudChartsRepository>()

    private lateinit var repository: WaveRepositoryImpl

    @Before
    fun setUp() {
        repository = WaveRepositoryImpl(
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            soundCloudRepository = soundCloudRepository,
            chartsRepository = chartsRepository,
        )
    }

    @Test
    fun `combines likes history and genre discoveries without duplicates`() = runBlocking {
        val liked = (1..12).map { track("l$it", "Rock") }
        val history = (1..5).map { track("h$it", null) }
        val searchResults = listOf(
            track("g1", "Rock"),
            track("g2", "Rock"),
            track("g3", "Rock"),
            track("l1", "Rock"),
            track("h1", null),
        )

        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        every { playHistoryRepository.getPlayHistory() } returns flowOf(history)
        coEvery { soundCloudRepository.search("Rock", any()) } returns Result.success(searchResults)
        coEvery { chartsRepository.getTop50() } returns Result.success(emptyList())

        val result = repository.generateWave().getOrThrow()

        assertEquals(18, result.tracks.size)
        assertEquals(10, result.fromLikes)
        assertEquals(5, result.fromHistory)
        assertEquals(3, result.fromGenres)
        assertEquals(result.tracks.size, result.tracks.distinctBy { it.id }.size)
    }

    @Test
    fun `falls back to charts when likes have no genres`() = runBlocking {
        every { likedTracksRepository.getLikedTracks() } returns flowOf(
            (1..3).map { track("l$it", null) },
        )
        every { playHistoryRepository.getPlayHistory() } returns flowOf(emptyList())
        coEvery { chartsRepository.getTop50() } returns Result.success(
            (1..7).map { track("c$it", null) },
        )

        val result = repository.generateWave().getOrThrow()

        assertEquals(8, result.tracks.size)
        assertEquals(3, result.fromLikes)
        assertEquals(5, result.fromGenres)
        coVerify(exactly = 0) { soundCloudRepository.search(any(), any()) }
    }

    @Test
    fun `empty sources yield an empty wave`() = runBlocking {
        every { likedTracksRepository.getLikedTracks() } returns flowOf(emptyList())
        every { playHistoryRepository.getPlayHistory() } returns flowOf(emptyList())
        coEvery { chartsRepository.getTop50() } returns Result.success(emptyList())

        val result = repository.generateWave().getOrThrow()

        assertTrue(result.tracks.isEmpty())
        assertEquals(0, result.fromLikes)
        assertEquals(0, result.fromHistory)
        assertEquals(0, result.fromGenres)
    }

    private fun track(id: String, genre: String?) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = genre,
        transcodingUrl = "https://soundcloud.com/test/$id",
    )
}
