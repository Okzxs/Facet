package com.my.music.feature.home

import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Группировка «Твои жанры» живёт в HomeViewModel
 * (отдельного CollectionsViewModel в проекте нет).
 */
class HomeViewModelGenreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newAndHotFlow = MutableStateFlow<List<Track>>(emptyList())
    private val top50Flow = MutableStateFlow<List<Track>>(emptyList())
    private val historyFlow = MutableStateFlow<List<Track>>(emptyList())
    private val likedFlow = MutableStateFlow<List<Track>>(emptyList())
    private val memoriesFlow = MutableStateFlow<List<Track>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        val chartsRepository = mockk<SoundCloudChartsRepository>()
        val likedTracksRepository = mockk<LikedTracksRepository>()
        val playHistoryRepository = mockk<PlayHistoryRepository>()

        every { chartsRepository.observeNewAndHot() } returns newAndHotFlow
        every { chartsRepository.observeTop50() } returns top50Flow
        coEvery { chartsRepository.getNewAndHot(any()) } returns Result.success(emptyList())
        coEvery { chartsRepository.getTop50(any()) } returns Result.success(emptyList())
        every { likedTracksRepository.getLikedTracks() } returns likedFlow
        every { likedTracksRepository.getLikedTracksBySeason(any()) } returns memoriesFlow
        every { playHistoryRepository.getPlayHistory() } returns historyFlow

        viewModel = HomeViewModel(chartsRepository, likedTracksRepository, playHistoryRepository)
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
        memoriesFlow.value = listOf(track("m1", "Rock"))

        val state = viewModel.uiState.value

        assertEquals(listOf("m1"), state.memories.map { it.id })
    }

    @Test
    fun `chart caches are observed reactively`() {
        newAndHotFlow.value = listOf(track("n1", null))
        top50Flow.value = listOf(track("t1", null))

        val state = viewModel.uiState.value

        assertEquals(listOf("n1"), state.newAndHot.map { it.id })
        assertEquals(listOf("t1"), state.top50.map { it.id })
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
