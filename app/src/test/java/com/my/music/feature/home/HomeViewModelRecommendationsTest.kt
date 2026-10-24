package com.my.music.feature.home

import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.RecentlyShownRepository
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
import kotlin.random.Random

class HomeViewModelRecommendationsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val youtubeRepository = mockk<YouTubeRepository>()
    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()
    private val recentlyShownRepository = mockk<RecentlyShownRepository>()
    private val recommendationPlanner = RecommendationPlanner()

    private val historyFlow = MutableStateFlow<List<Track>>(emptyList())
    private val likedFlow = MutableStateFlow<List<Track>>(emptyList())
    private val memoriesFlow = MutableStateFlow<List<SeasonMemory>>(emptyList())
    private var recentlyShownIds = emptySet<String>()

    @Before
    fun setUp() {
        recommendationPlanner.random = Random(0)
        every { likedTracksRepository.getLikedTracks() } returns likedFlow
        every { likedTracksRepository.getSeasonMemories(any()) } returns memoriesFlow
        every { playHistoryRepository.getPlayHistory() } returns historyFlow
        coEvery {
            recentlyShownRepository.getRecentlyShownIds(any(), any())
        } answers { recentlyShownIds }
        coEvery {
            recentlyShownRepository.markShown(any(), any(), any())
        } answers {
            recentlyShownIds = firstArg<Collection<String>>().toSet()
        }
    }

    @Test
    fun `falls back to popular music when there are no likes or history`() {
        val popular = (1..12).map { index ->
            track(
                id = "popular-$index",
                title = "Popular Song $index",
                artist = "Artist $index",
            )
        }
        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(popular)

        val state = createViewModel().uiState.value

        assertEquals(12, state.recommendations.size)
        assertEquals(
            popular.map { it.id }.toSet(),
            state.recommendations.map { it.id }.toSet(),
        )
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 1) { youtubeRepository.search(POPULAR_QUERY, 24) }
        coVerify(exactly = 1) { recentlyShownRepository.markShown(any(), any(), any()) }
    }

    @Test
    fun `filters translations remixes covers and live versions`() {
        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(
            listOf(
                track("good-1", title = "Original Song", artist = "Artist One"),
                track("translated", title = "Original Song (перевод на русский)", artist = "Artist Two"),
                track("multilang", title = "Original Song на разных языках", artist = "Artist Three"),
                track("remix", title = "Original Song (Remix)", artist = "Artist Four"),
                track("cover", title = "Original Song (Cover)", artist = "Artist Five"),
                track("live", title = "Original Song (Live)", artist = "Artist Six"),
                track("instrumental", title = "Original Song (Instrumental)", artist = "Artist Seven"),
                track("karaoke", title = "Original Song (Karaoke)", artist = "Artist Eight"),
                track("weird", title = "🎵🎵🎵", artist = "Artist Nine"),
                track("good-2", title = "Другая песня", artist = "Artist Ten"),
            ),
        )

        val state = createViewModel().uiState.value

        assertEquals(setOf("good-1", "good-2"), state.recommendations.map { it.id }.toSet())
        coVerify(exactly = 1) { youtubeRepository.search(POPULAR_QUERY, 24) }
    }

    @Test
    fun `keeps recommendation in feed after it appears in history`() {
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.success(listOf(track("rec-1", title = "Recommended Song", artist = "Artist A")))

        val viewModel = createViewModel()
        assertEquals(listOf("rec-1"), viewModel.uiState.value.recommendations.map { it.id })

        historyFlow.value = listOf(track("rec-1", title = "Recommended Song", artist = "Artist A"))

        val state = viewModel.uiState.value
        assertEquals(listOf("rec-1"), state.recommendations.map { it.id })
        assertEquals(listOf("rec-1"), state.recentTracks.map { it.id })
        coVerify(exactly = 1) { youtubeRepository.search(any(), any()) }
    }

    @Test
    fun `refresh excludes recently shown tracks and returns a new batch`() {
        var searchRound = 0
        coEvery { youtubeRepository.search(any(), any()) } answers {
            searchRound += 1
            if (searchRound == 1) {
                Result.success(
                    listOf(
                        track("rec-1", title = "First Song", artist = "Artist A"),
                        track("rec-2", title = "Second Song", artist = "Artist B"),
                    ),
                )
            } else {
                Result.success(
                    listOf(
                        track("rec-1", title = "First Song", artist = "Artist A"),
                        track("rec-2", title = "Second Song", artist = "Artist B"),
                        track("rec-3", title = "Third Song", artist = "Artist C"),
                        track("rec-4", title = "Fourth Song", artist = "Artist D"),
                    ),
                )
            }
        }

        val viewModel = createViewModel()
        assertEquals(
            setOf("rec-1", "rec-2"),
            viewModel.uiState.value.recommendations.map { it.id }.toSet(),
        )

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(
            setOf("rec-3", "rec-4"),
            state.recommendations.map { it.id }.toSet(),
        )
        assertTrue(state.recommendations.none { it.id in setOf("rec-1", "rec-2") })
        assertFalse(state.isRefreshing)
        assertFalse(state.isRecommendationsLoading)
        assertNull(state.recommendationsError)
        coVerify(exactly = 2) { youtubeRepository.search(any(), any()) }
    }

    @Test
    fun `failed refresh keeps previous recommendations and exposes error`() {
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.success(listOf(track("rec-1", title = "Recommended Song", artist = "Artist A")))

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

    @Test
    fun `validates artist metadata and rejects seed name in another artist title`() {
        likedFlow.value = listOf(
            track("seed-like", title = "Liked Song", artist = "Dark Prince", genre = "Pop"),
        )
        coEvery { youtubeRepository.search(any(), any()) } answers {
            if (firstArg<String>() == POPULAR_QUERY) {
                Result.success(emptyList())
            } else {
                Result.success(
                    listOf(
                        track("valid", title = "Night Song", artist = "Dark Prince"),
                        track(
                            "impostor",
                            title = "Dark Prince - Night Song",
                            artist = "Other Artist",
                        ),
                        track("title-match", title = "Dark Prince - Intro", artist = "Unknown"),
                        track("title-prefix-fail", title = "The Dark Prince - Song", artist = ""),
                    ),
                )
            }
        }

        val state = createViewModel().uiState.value

        assertEquals(
            setOf("valid", "title-match"),
            state.recommendations.map { it.id }.toSet(),
        )
        assertTrue(state.recommendations.none { it.id == "impostor" })
        assertTrue(state.recommendations.none { it.id == "title-prefix-fail" })
        coVerify(exactly = 1) { youtubeRepository.search(POPULAR_QUERY, 24) }
    }

    @Test
    fun `caps tracks per artist and tops up with popular tracks`() {
        likedFlow.value = listOf(
            track("seed-like", title = "Liked Song", artist = "Seed Artist", genre = null),
        )
        val primary = (1..12).map { index ->
            track(
                id = "seed-$index",
                title = "Seed Song $index",
                artist = "Seed Artist",
            )
        }
        val fallback = (1..4).map { index ->
            track(
                id = "fallback-seed-$index",
                title = "Fallback Seed Song $index",
                artist = "Seed Artist",
            )
        } + (1..12).map { index ->
            track(
                id = "other-$index",
                title = "Other Song $index",
                artist = "Other Artist $index",
            )
        }
        coEvery { youtubeRepository.search(any(), any()) } answers {
            if (firstArg<String>() == POPULAR_QUERY) {
                Result.success(fallback)
            } else {
                Result.success(primary)
            }
        }

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        val perArtist = state.recommendations.groupingBy { it.artist }.eachCount()
        assertEquals(3, perArtist["Seed Artist"])
        assertTrue(perArtist.values.all { it <= 3 })
        coVerify(exactly = 1) { youtubeRepository.search(POPULAR_QUERY, 24) }
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            youtubeRepository = youtubeRepository,
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            recentlyShownRepository = recentlyShownRepository,
            recommendationPlanner = recommendationPlanner,
        )

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
