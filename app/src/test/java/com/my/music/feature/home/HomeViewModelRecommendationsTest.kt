package com.my.music.feature.home

import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.YouTubeRepository
import com.my.music.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelRecommendationsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val youtubeRepository = mockk<YouTubeRepository>()
    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()

    private val historyFlow = MutableStateFlow<List<Track>>(emptyList())
    private val likedFlow = MutableStateFlow<List<Track>>(emptyList())
    private val memoriesFlow = MutableStateFlow<List<SeasonMemory>>(emptyList())

    @Before
    fun setUp() {
        every { likedTracksRepository.getLikedTracks() } returns likedFlow
        every { likedTracksRepository.getSeasonMemories(any()) } returns memoriesFlow
        every { playHistoryRepository.getPlayHistory() } returns historyFlow
    }

    @Test
    fun `builds personalized recommendations from likes and history`() {
        likedFlow.value = listOf(
            track("l1", artist = "Artist B", genre = null),
        )
        historyFlow.value = listOf(
            track("h1", artist = "Artist A", genre = "Rock"),
        )
        coEvery {
            youtubeRepository.search("Rock music mix", 12)
        } returns Result.success(
            listOf(
                track("rec-1", durationMs = 180_000L),
                track("h1", durationMs = 180_000L),
                track("too-long", durationMs = 1_200_000L),
            ),
        )
        coEvery {
            youtubeRepository.search("Artist B best songs", 12)
        } returns Result.success(
            listOf(
                track("rec-2", durationMs = 200_000L),
                track("rec-1", durationMs = 180_000L),
            ),
        )

        val state = createViewModel().uiState.value

        assertEquals(listOf("rec-1", "rec-2"), state.recommendations.map { it.id })
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search("Rock music mix", 12) }
        coVerify(exactly = 1) { youtubeRepository.search("Artist B best songs", 12) }
        coVerify(exactly = 0) { youtubeRepository.search("popular music 2026", 12) }
    }

    @Test
    fun `falls back to popular music when there are no likes or history`() {
        coEvery {
            youtubeRepository.search("popular music 2026", 12)
        } returns Result.success(listOf(track("popular-1")))

        val state = createViewModel().uiState.value

        assertEquals(listOf("popular-1"), state.recommendations.map { it.id })
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search("popular music 2026", 12) }
    }

    @Test
    fun `falls back to popular music when personalized search finds nothing`() {
        likedFlow.value = listOf(
            track("l1", artist = "Artist B", genre = "Rock"),
        )
        coEvery {
            youtubeRepository.search("Rock music mix", 12)
        } returns Result.success(listOf(track("too-long", durationMs = 1_200_000L)))
        coEvery {
            youtubeRepository.search("Artist B best songs", 12)
        } returns Result.success(listOf(track("l1")))
        coEvery {
            youtubeRepository.search("popular music 2026", 12)
        } returns Result.success(listOf(track("popular-1")))

        val state = createViewModel().uiState.value

        assertEquals(listOf("popular-1"), state.recommendations.map { it.id })
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search("popular music 2026", 12) }
    }

    @Test
    fun `failed refresh keeps previous recommendations and exposes error`() {
        coEvery {
            youtubeRepository.search("popular music 2026", 12)
        } returns Result.success(listOf(track("popular-1")))

        val viewModel = createViewModel()
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.failure(IllegalStateException("network"))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf("popular-1"), state.recommendations.map { it.id })
        assertEquals("network", state.recommendationsError)
        assertTrue(state.recommendations.isNotEmpty())
        assertFalse(state.isRefreshing)
        assertFalse(state.isRecommendationsLoading)
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(youtubeRepository, likedTracksRepository, playHistoryRepository)

    private fun track(
        id: String,
        durationMs: Long = 120_000L,
        artist: String = "Artist",
        genre: String? = "Pop",
    ) = Track(
        id = id,
        source = Source.YOUTUBE,
        title = "Title $id",
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = genre,
        transcodingUrl = "https://youtube.com/watch?v=$id",
    )
}
