package com.my.music.feature.home

import com.my.music.data.local.dao.DislikedTrackDao
import com.my.music.data.local.entity.DislikedTrackEntity
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.RecentlyShownRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.YouTubeRepository
import com.my.music.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()
    private val recentlyShownRepository = mockk<RecentlyShownRepository>()
    private val dislikedTrackDao = mockk<DislikedTrackDao>()
    private var chartTracks: List<Track> = emptyList()
    private val chartsRepository = object : SoundCloudChartsRepository {
        override fun observeNewAndHot() = flowOf(emptyList<Track>())
        override fun observeTop50() = flowOf(chartTracks)
        override suspend fun getNewAndHot(forceRefresh: Boolean) = Result.success(emptyList<Track>())
        override suspend fun getTop50(forceRefresh: Boolean) = Result.success(chartTracks)
    }
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
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
        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { youtubeRepository.getRelated(any(), any()) } returns Result.success(emptyList())
        coEvery { soundCloudRepository.getRelated(any(), any()) } returns Result.success(emptyList())
        coEvery {
            recentlyShownRepository.getRecentlyShownIds(any(), any())
        } answers { recentlyShownIds }
        coEvery {
            recentlyShownRepository.markShown(any(), any(), any())
        } answers {
            recentlyShownIds = firstArg<Collection<String>>().toSet()
        }
        coEvery { dislikedTrackDao.getAll() } returns emptyList()
        // Холодный старт идёт через чарты; в тестах чарт пуст, поэтому
        // срабатывает YouTube-фолбэк, как и раньше.
        every { downloadRepository.observeDownloadedIds() } returns flowOf(emptySet())
    }

    @Test
    fun `falls back to popular music only when there are no likes or history`() {
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
        coVerify(exactly = 0) { youtubeRepository.getRelated(any(), any()) }
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
    fun `keeps related recommendation in feed after it appears in history`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        coEvery { youtubeRepository.getRelated(any(), any()) } returns
            Result.success(
                listOf(track("rec-1", title = "Recommended Song", artist = "Artist A")),
            )

        val viewModel = createViewModel()
        assertEquals(setOf("rec-1"), viewModel.uiState.value.recommendations.map { it.id }.toSet())

        historyFlow.value = listOf(track("rec-1", title = "Recommended Song", artist = "Artist A"))

        val state = viewModel.uiState.value
        assertEquals(setOf("rec-1"), state.recommendations.map { it.id }.toSet())
        assertEquals(listOf("rec-1"), state.recentTracks.map { it.id })
        coVerify(exactly = 1) { youtubeRepository.getRelated(any(), 30) }
    }

    @Test
    fun `refresh excludes recently shown tracks and returns a new batch`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        var relatedRound = 0
        coEvery { youtubeRepository.getRelated(any(), any()) } answers {
            relatedRound += 1
            if (relatedRound == 1) {
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
        coVerify(exactly = 2) { youtubeRepository.getRelated(any(), 30) }
    }

    @Test
    fun `failed refresh keeps previous recommendations and exposes error`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        coEvery { youtubeRepository.getRelated(any(), any()) } returns
            Result.success(listOf(track("rec-1", title = "Recommended Song", artist = "Artist A")))

        val viewModel = createViewModel()
        assertEquals(setOf("rec-1"), viewModel.uiState.value.recommendations.map { it.id }.toSet())

        coEvery { youtubeRepository.getRelated(any(), any()) } returns
            Result.failure(IllegalStateException("network"))
        coEvery { youtubeRepository.search(any(), any()) } returns
            Result.failure(IllegalStateException("network"))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(setOf("rec-1"), state.recommendations.map { it.id }.toSet())
        assertEquals("network", state.recommendationsError)
        assertTrue(state.recommendations.isNotEmpty())
        assertFalse(state.isRefreshing)
        assertFalse(state.isRecommendationsLoading)
    }

    @Test
    fun `uses related items as main source and accepts other artists`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        val valid = (0 until 8).flatMap { artistIndex ->
            (0 until 3).map { trackIndex ->
                track(
                    id = "related-$artistIndex-$trackIndex",
                    title = "Related Song $artistIndex-$trackIndex",
                    artist = "Related Artist $artistIndex",
                )
            }
        }
        val invalid = listOf(
            track("seed-like", title = "Liked Song", artist = "Seed Artist"),
            track("short", title = "Short Song", artist = "Short Artist", durationMs = 30_000L),
            track("long", title = "Long Song", artist = "Long Artist", durationMs = 1_000_000L),
            track("live", title = "Live Song (Live)", artist = "Live Artist"),
            track("remix", title = "Remix Song (Remix)", artist = "Remix Artist"),
        )
        coEvery { youtubeRepository.getRelated(any(), any()) } returns
            Result.success(valid + invalid)

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        assertTrue(state.recommendations.map { it.artist }.toSet().size > 1)
        assertTrue(state.recommendations.none { it.id in invalid.map(Track::id) })
        coVerify(exactly = 1) {
            youtubeRepository.getRelated("https://youtube.com/watch?v=seed-like", 30)
        }
        coVerify(exactly = 0) { youtubeRepository.search(any(), any()) }
    }

    @Test
    fun `uses soundcloud related for soundcloud seeds`() {
        val seed = track(
            id = "sc-seed",
            artist = "SC Seed",
            source = Source.SOUNDCLOUD,
            transcodingUrl = "https://soundcloud.com/test/sc-seed",
        )
        likedFlow.value = listOf(seed)
        val related = (0 until 8).flatMap { artistIndex ->
            (0 until 2).map { trackIndex ->
                track(
                    id = "sc-related-$artistIndex-$trackIndex",
                    artist = "SC Related $artistIndex",
                    source = Source.SOUNDCLOUD,
                    transcodingUrl = "https://soundcloud.com/test/sc-related-$artistIndex-$trackIndex",
                )
            }
        }
        coEvery { soundCloudRepository.getRelated(any(), any()) } returns Result.success(related)

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        coVerify(exactly = 1) {
            soundCloudRepository.getRelated("https://soundcloud.com/test/sc-seed", 30)
        }
        coVerify(exactly = 0) { youtubeRepository.getRelated(any(), any()) }
    }

    @Test
    fun `excludes seed and recently shown tracks from related items`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        recentlyShownIds = setOf("shown-1")
        val related = listOf(
            track("seed-like", artist = "Seed Artist"),
            track("shown-1", artist = "Shown Artist"),
        ) + (0 until 8).flatMap { artistIndex ->
            (0 until 2).map { trackIndex ->
                track(
                    id = "fresh-$artistIndex-$trackIndex",
                    artist = "Fresh Artist $artistIndex",
                )
            }
        }
        coEvery { youtubeRepository.getRelated(any(), any()) } returns Result.success(related)

        val state = createViewModel().uiState.value

        assertEquals(15, state.recommendations.size)
        assertTrue(state.recommendations.none { it.id == "seed-like" })
        assertTrue(state.recommendations.none { it.id == "shown-1" })
    }

    @Test
    fun `falls back to strict artist search when related returns fewer than ten`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        val related = (0 until 4).map { index ->
            track(
                id = "related-other-$index",
                artist = "Related Other $index",
            )
        }
        val fallback = (1..8).map { index ->
            track(
                id = "fallback-seed-$index",
                title = "Fallback Seed Song $index",
                artist = "Seed Artist",
            )
        } + (1..8).map { index ->
            track(
                id = "fallback-other-$index",
                title = "Fallback Other Song $index",
                artist = "Fallback Other $index",
            )
        } + track(
            id = "fallback-impostor",
            title = "Seed Artist - Night Song",
            artist = "Impostor",
        )
        coEvery { youtubeRepository.getRelated(any(), any()) } returns Result.success(related)
        val searchQueries = mutableListOf<String>()
        coEvery { youtubeRepository.search(capture(searchQueries), any()) } returns
            Result.success(fallback)

        val state = createViewModel().uiState.value

        assertEquals(6, state.recommendations.size)
        assertEquals(2, state.recommendations.count { it.artist == "Seed Artist" })
        assertTrue(state.recommendations.none { it.id.startsWith("fallback-other-") })
        assertTrue(state.recommendations.none { it.id == "fallback-impostor" })
        assertTrue(searchQueries.isNotEmpty())
        assertTrue(searchQueries.none { it == POPULAR_QUERY })
    }

    @Test
    fun `does not use popular fallback when personal data exists but related is empty`() {
        likedFlow.value = listOf(track("seed-like", artist = "Seed Artist"))
        val searchQueries = mutableListOf<String>()
        coEvery { youtubeRepository.search(capture(searchQueries), any()) } returns
            Result.success(emptyList())

        val state = createViewModel().uiState.value

        assertTrue(state.recommendations.isEmpty())
        assertNull(state.recommendationsError)
        assertTrue(searchQueries.isNotEmpty())
        assertTrue(searchQueries.none { it == POPULAR_QUERY })
    }

    @Test
    fun `excludes disliked tracks from recommendations`() {
        val popular = (1..12).map { index ->
            track(
                id = "popular-$index",
                title = "Popular Song $index",
                artist = "Artist $index",
            )
        }
        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(popular)
        coEvery { dislikedTrackDao.getAll() } returns listOf(
            DislikedTrackEntity(trackId = "popular-3", dislikedAt = 1_000L),
        )

        val state = createViewModel().uiState.value

        assertEquals(11, state.recommendations.size)
        assertTrue(state.recommendations.none { it.id == "popular-3" })
    }

    @Test
    fun `requests related items for four to six seed tracks`() {
        likedFlow.value = (0 until 8).map { index ->
            track(
                id = "liked-$index",
                artist = "Liked Artist $index",
            )
        }
        val relatedUrls = mutableListOf<String>()
        coEvery { youtubeRepository.getRelated(capture(relatedUrls), any()) } returns
            Result.success(emptyList())

        createViewModel()

        assertTrue(relatedUrls.size in 4..6)
        assertEquals(relatedUrls.size, relatedUrls.toSet().size)
    }

    @Test
    fun `allows third related track only for dominant artist`() {
        likedFlow.value = (1..3).map { index ->
            track(
                id = "dominant-liked-$index",
                artist = "Dominant Artist",
            )
        }
        val related = (1..5).map { index ->
            track(
                id = "related-dominant-$index",
                artist = "Dominant Artist",
            )
        } + (1..5).map { index ->
            track(
                id = "related-other-$index",
                artist = "Other Artist",
            )
        }
        coEvery { youtubeRepository.getRelated(any(), any()) } returns Result.success(related)

        val state = createViewModel().uiState.value

        val perArtist = state.recommendations.groupingBy { it.artist }.eachCount()
        assertEquals(3, perArtist["Dominant Artist"])
        assertEquals(2, perArtist["Other Artist"])
        assertEquals(5, state.recommendations.size)
    }

    @Test
    fun `cold start prefers SoundCloud charts over popular search`() {
        val chart = listOf(
            track("chart-1", title = "Chart One", artist = "Artist A"),
            track("chart-2", title = "Chart Two", artist = "Artist B"),
        )
        chartTracks = chart

        val state = createViewModel().uiState.value

        assertEquals(setOf("chart-1", "chart-2"), state.recommendations.map { it.id }.toSet())
        coVerify(exactly = 0) { youtubeRepository.search(POPULAR_QUERY, 24) }
    }

    @Test
    fun `collapses duplicate uploads of the same track`() {
        coEvery { youtubeRepository.search(any(), any()) } returns Result.success(
            listOf(
                track("dup-1", title = "Same Song", artist = "Artist X"),
                track("dup-2", title = "Same Song (Official Video)", artist = "Artist X"),
                track("other", title = "Other Song", artist = "Artist Y"),
            ),
        )

        val state = createViewModel().uiState.value

        assertEquals(2, state.recommendations.size)
        assertEquals(
            setOf("dup-1", "other"),
            state.recommendations.map { it.id }.toSet(),
        )
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            youtubeRepository = youtubeRepository,
            soundCloudRepository = soundCloudRepository,
            chartsRepository = chartsRepository,
            downloadRepository = mockk(relaxed = true),
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            recentlyShownRepository = recentlyShownRepository,
            dislikedTrackDao = dislikedTrackDao,
            recommendationPlanner = recommendationPlanner,
        )

    private fun track(
        id: String,
        title: String = "Title $id",
        durationMs: Long = 180_000L,
        artist: String = "Artist",
        genre: String? = "Pop",
        source: Source = Source.YOUTUBE,
        transcodingUrl: String? = "https://youtube.com/watch?v=$id",
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = genre,
        transcodingUrl = transcodingUrl,
    )
}
