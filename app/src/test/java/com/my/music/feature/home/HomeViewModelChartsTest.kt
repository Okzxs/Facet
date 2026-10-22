package com.my.music.feature.home

import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.YouTubeRepository
import com.my.music.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelChartsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val youtubeRepository = mockk<YouTubeRepository>()
    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()

    @Test
    fun `loads top ru and top us from youtube in parallel`() {
        coEvery {
            youtubeRepository.search("Top 100 Russia music", 8)
        } returns Result.success(
            listOf(
                track("ru-short", durationMs = 20_000L),
                track("ru-1", durationMs = 180_000L),
            ),
        )
        coEvery {
            youtubeRepository.search("Top 100 USA music", 8)
        } returns Result.success(listOf(track("us-1", durationMs = 200_000L)))
        stubLocalData()

        val state = createViewModel().uiState.value

        assertEquals(listOf("ru-1"), state.topRu.map { it.id })
        assertEquals(listOf("us-1"), state.topUs.map { it.id })
        assertFalse(state.isChartsLoading)
        assertNull(state.chartsError)
    }

    @Test
    fun `keeps the successful region and reports the failed one`() {
        coEvery {
            youtubeRepository.search("Top 100 Russia music", 8)
        } returns Result.success(listOf(track("ru-1")))
        coEvery {
            youtubeRepository.search("Top 100 USA music", 8)
        } returns Result.failure(IllegalStateException("network"))
        stubLocalData()

        val state = createViewModel().uiState.value

        assertEquals(listOf("ru-1"), state.topRu.map { it.id })
        assertTrue(state.topUs.isEmpty())
        assertEquals("Не удалось загрузить Top US", state.chartsError)
    }

    @Test
    fun `failed refresh keeps previously loaded charts`() {
        coEvery {
            youtubeRepository.search("Top 100 Russia music", 8)
        } returns Result.success(listOf(track("ru-1")))
        coEvery {
            youtubeRepository.search("Top 100 USA music", 8)
        } returns Result.success(listOf(track("us-1")))
        stubLocalData()

        val viewModel = createViewModel()
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.failure(IllegalStateException("network"))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf("ru-1"), state.topRu.map { it.id })
        assertEquals(listOf("us-1"), state.topUs.map { it.id })
        assertNull(state.chartsError)
        assertFalse(state.isRefreshing)
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(youtubeRepository, likedTracksRepository, playHistoryRepository)

    private fun stubLocalData() {
        every { playHistoryRepository.getPlayHistory() } returns
            MutableStateFlow<List<Track>>(emptyList())
        every { likedTracksRepository.getLikedTracks() } returns
            MutableStateFlow<List<Track>>(emptyList())
        every { likedTracksRepository.getSeasonMemories(any()) } returns
            MutableStateFlow<List<SeasonMemory>>(emptyList())
    }

    private fun track(
        id: String,
        durationMs: Long = 120_000L,
    ) = Track(
        id = id,
        source = Source.YOUTUBE,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = "Pop",
        transcodingUrl = "https://youtube.com/watch?v=$id",
    )
}
