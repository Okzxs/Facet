package com.facet.music.feature.home

import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.RecentlyShownRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

/**
 * Группировка «Твои жанры» живёт в HomeViewModel
 * (отдельного CollectionsViewModel в проекте нет).
 */
class HomeViewModelGenreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val historyFlow = MutableStateFlow<List<Track>>(emptyList())
    private val likedFlow = MutableStateFlow<List<Track>>(emptyList())
    private val memoriesFlow = MutableStateFlow<List<SeasonMemory>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        val youtubeRepository = mockk<YouTubeRepository>()
        val soundCloudRepository = mockk<SoundCloudRepository>()
        val likedTracksRepository = mockk<LikedTracksRepository>()
        val playHistoryRepository = mockk<PlayHistoryRepository>()
        val recentlyShownRepository = mockk<RecentlyShownRepository>()
        val dislikedTrackDao = mockk<DislikedTrackDao>()
        val recommendationPlanner = RecommendationPlanner().apply {
            random = Random(0)
        }

        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { youtubeRepository.getRelated(any(), any()) } returns Result.success(emptyList())
        coEvery { soundCloudRepository.getRelated(any(), any()) } returns Result.success(emptyList())
        every { likedTracksRepository.getLikedTracks() } returns likedFlow
        every { likedTracksRepository.getSeasonMemories(any()) } returns memoriesFlow
        every { playHistoryRepository.getPlayHistory() } returns historyFlow
        coEvery {
            recentlyShownRepository.getRecentlyShownIds(any(), any())
        } returns emptySet()
        coEvery {
            recentlyShownRepository.markShown(any(), any(), any())
        } just Runs
        coEvery { dislikedTrackDao.getAll() } returns emptyList()

        viewModel = HomeViewModel(
            youtubeRepository = youtubeRepository,
            soundCloudRepository = soundCloudRepository,
            chartsRepository = mockk(relaxed = true),
            downloadRepository = mockk(relaxed = true),
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            recentlyShownRepository = recentlyShownRepository,
            dislikedTrackDao = dislikedTrackDao,
            recommendationPlanner = recommendationPlanner,
            deezerProvider = mockk(relaxed = true),
        )
    }

    @Test
    fun `groups genres from history and likes with deduplication and trimming`() {
        historyFlow.value = listOf(
            track("h1", "Rock"),
            track("h2", " Pop "),
        )
        likedFlow.value = listOf(
            track("l1", "Rock"),
            track("h1", "Rock"),
            track("l2", null),
            track("l3", "   "),
        )

        val state = viewModel.uiState.value

        assertEquals(listOf("Rock", "Pop"), state.genreSections.map { it.genre })
        assertEquals(2, state.genreSections[0].tracks.size)
        assertEquals(1, state.genreSections[1].tracks.size)
        assertEquals(setOf("h1", "l1"), state.genreSections[0].tracks.map { it.id }.toSet())
        assertEquals(listOf("h1", "h2"), state.recentTracks.map { it.id })
        assertEquals(listOf("l1", "h1", "l2", "l3"), state.likedTracks.map { it.id })
    }

    @Test
    fun `groups genres from likes when history is empty`() {
        likedFlow.value = listOf(
            track("l1", "Jazz"),
            track("l2", "Jazz"),
            track("l3", "Electronic"),
        )

        val state = viewModel.uiState.value

        assertEquals(listOf("Jazz", "Electronic"), state.genreSections.map { it.genre })
        assertEquals(2, state.genreSections.first().tracks.size)
        assertTrue(state.recentTracks.isEmpty())
    }

    @Test
    fun `recent tracks are limited to twenty`() {
        historyFlow.value = (1..25).map { track("h$it", "Rock") }

        val state = viewModel.uiState.value

        assertEquals(20, state.recentTracks.size)
        assertEquals("h1", state.recentTracks.first().id)
        assertEquals("h20", state.recentTracks.last().id)
        assertEquals(20, state.genreSections.single().tracks.size)
    }

    @Test
    fun `memories reflect likes of the current season`() {
        memoriesFlow.value = listOf(
            SeasonMemory(
                track = track("m1", "Rock"),
                season = "Autumn",
                latitude = 55.7558,
                longitude = 37.6173,
                likedAt = 1_000L,
            ),
        )

        val state = viewModel.uiState.value

        assertEquals(listOf("m1"), state.memories.map { it.track.id })
        assertEquals(55.7558, state.memories.single().latitude ?: 0.0, 0.0001)
        assertEquals(37.6173, state.memories.single().longitude ?: 0.0, 0.0001)
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
