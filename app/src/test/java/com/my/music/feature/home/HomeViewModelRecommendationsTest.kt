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
        likedFlow.value = listOf(track("l1", artist = "Artist B", genre = null))
        historyFlow.value = listOf(track("h1", artist = "Artist A", genre = "Rock"))
        coEvery {
            youtubeRepository.search("Rock official audio", 24)
        } returns Result.success(
            (1..20).map { index ->
                track("rec-$index", title = "Original Song $index")
            },
        )
        coEvery {
            youtubeRepository.search("Artist B official audio", 24)
        } returns Result.success(emptyList())

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        assertEquals("rec-1", state.recommendations.first().id)
        assertEquals("rec-15", state.recommendations.last().id)
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search("Rock official audio", 24) }
        coVerify(exactly = 1) { youtubeRepository.search("Artist B official audio", 24) }
        coVerify(exactly = 0) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `filters translations remixes covers and live versions`() {
        likedFlow.value = listOf(track("l1", artist = "Band", genre = "Rock"))
        coEvery {
            youtubeRepository.search("Rock official audio", 24)
        } returns Result.success(
            listOf(
                track("good-1", title = "Original Song"),
                track("translated", title = "Original Song (перевод на русский)"),
                track("multilang", title = "Original Song на разных языках"),
                track("remix", title = "Original Song (Remix)"),
                track("cover", title = "Original Song (Cover)"),
                track("live", title = "Original Song (Live)"),
                track("instrumental", title = "Original Song (Instrumental)"),
                track("karaoke", title = "Original Song (Karaoke)"),
                track("weird", title = "🎵🎵🎵"),
                track("good-2", title = "Другая песня"),
            ),
        )
        coEvery {
            youtubeRepository.search("Band official audio", 24)
        } returns Result.success(emptyList())
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(emptyList())

        val state = createViewModel().uiState.value

        assertEquals(listOf("good-1", "good-2"), state.recommendations.map { it.id })
        coVerify(exactly = 1) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `tops up short personalized selection with popular tracks`() {
        likedFlow.value = listOf(track("l1", artist = "Band", genre = "Rock"))
        coEvery {
            youtubeRepository.search("Rock official audio", 24)
        } returns Result.success(
            listOf(
                track("genre-1", title = "Genre One"),
                track("genre-2", title = "Genre Two"),
            ),
        )
        coEvery {
            youtubeRepository.search("Band official audio", 24)
        } returns Result.success(
            listOf(
                track("artist-1", title = "Artist One"),
                track("artist-2", title = "Artist Two"),
                track("artist-3", title = "Artist Three"),
            ),
        )
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(
            (1..10).map { index ->
                track("popular-$index", title = "Popular Song $index")
            },
        )

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        assertEquals(
            listOf("genre-1", "genre-2", "artist-1", "artist-2", "artist-3"),
            state.recommendations.take(5).map { it.id },
        )
        assertEquals("popular-10", state.recommendations.last().id)
        coVerify(exactly = 1) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `falls back to popular music when there are no likes or history`() {
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(
            (1..12).map { index ->
                track("popular-$index", title = "Popular Song $index")
            },
        )

        val state = createViewModel().uiState.value

        assertEquals(12, state.recommendations.size)
        assertEquals("popular-1", state.recommendations.first().id)
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `keeps recommendation in feed after it appears in history`() {
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(listOf(track("rec-1", title = "Recommended Song")))

        val viewModel = createViewModel()
        assertEquals(listOf("rec-1"), viewModel.uiState.value.recommendations.map { it.id })

        historyFlow.value = listOf(track("rec-1", title = "Recommended Song"))

        val state = viewModel.uiState.value
        assertEquals(listOf("rec-1"), state.recommendations.map { it.id })
        assertEquals(listOf("rec-1"), state.recentTracks.map { it.id })
        coVerify(exactly = 1) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `refresh reloads recommendations`() {
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(listOf(track("rec-1", title = "First Song")))

        val viewModel = createViewModel()
        assertEquals(listOf("rec-1"), viewModel.uiState.value.recommendations.map { it.id })

        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(listOf(track("rec-2", title = "Second Song")))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf("rec-2"), state.recommendations.map { it.id })
        assertFalse(state.isRefreshing)
        assertFalse(state.isRecommendationsLoading)
        coVerify(exactly = 2) { youtubeRepository.search("popular songs official audio", 24) }
    }

    @Test
    fun `failed refresh keeps previous recommendations and exposes error`() {
        coEvery {
            youtubeRepository.search("popular songs official audio", 24)
        } returns Result.success(listOf(track("rec-1", title = "Recommended Song")))

        val viewModel = createViewModel()
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.failure(IllegalStateException("network"))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf("rec-1"), state.recommendations.map { it.id })
        assertEquals("network", state.recommendationsError)
        assertTrue(state.recommendations.isNotEmpty())
        assertFalse(state.isRefreshing)
        assertFalse(state.isRecommendationsLoading)
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(youtubeRepository, likedTracksRepository, playHistoryRepository)

    private fun track(
        id: String,
        title: String = "Title $id",
        durationMs: Long = 180_000L,
        artist: String = "Artist",
        genre: String? = "Pop",
    ) = Track(
        id = id,
        source = Source.YOUTUBE,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = genre,
        transcodingUrl = "https://youtube.com/watch?v=$id",
    )
}
