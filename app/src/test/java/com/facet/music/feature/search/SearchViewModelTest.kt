package com.facet.music.feature.search

import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.network.ConnectivityObserver
import com.facet.music.core.search.SearchHistoryStore
import com.facet.music.core.search.SearchSuggestions
import com.facet.music.core.service.ServiceHealth
import com.facet.music.core.service.ServiceStatusManager
import com.facet.music.core.service.ServiceType
import com.facet.music.core.source.PlayableSourceRegistry
import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val soundCloudRepository = mockk<SoundCloudRepository>(relaxed = true)
    private val youTubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val statusManager = mockk<ServiceStatusManager>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val downloadRepository = mockk<DownloadRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val suggestions = mockk<SearchSuggestions>(relaxed = true)

    @Test
    fun `source toggle updates set, saves to settings, and blocks removing last source`() = runTest {
        stubDefaults()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSourceToggle(ServiceType.SPOTIFY)
        assertEquals(setOf(ServiceType.SOUNDCLOUD, ServiceType.YOUTUBE), viewModel.uiState.value.selectedSources)
        coVerify { settingsRepository.setSearchSources(setOf(ServiceType.SOUNDCLOUD, ServiceType.YOUTUBE)) }

        viewModel.onSourceToggle(ServiceType.YOUTUBE)
        assertEquals(setOf(ServiceType.SOUNDCLOUD), viewModel.uiState.value.selectedSources)

        viewModel.onSourceToggle(ServiceType.SOUNDCLOUD)
        assertEquals(setOf(ServiceType.SOUNDCLOUD), viewModel.uiState.value.selectedSources)
        assertNotNull(viewModel.uiState.value.sourceHint)

        viewModel.onAllSourcesSelected()
        assertEquals(ServiceType.entries.toSet(), viewModel.uiState.value.selectedSources)
        assertNull(viewModel.uiState.value.sourceHint)
    }

    @Test
    fun `featured artist is recognized and triggers loading artist tracks`() = runTest {
        stubDefaults(setOf(ServiceType.YOUTUBE))
        val odesza = Artist(
            id = "yt-odesza",
            source = Source.YOUTUBE,
            name = "ODESZA",
            avatarUrl = "https://img/odesza.jpg",
            subscriberCount = 1_000_000L,
            isVerified = true,
            url = "https://youtube.com/channel/odesza",
        )
        val topTrack = track("yt-top-1", "ODESZA", "A Moment Apart", Source.YOUTUBE)
        coEvery { youTubeRepository.searchPage("odes", any(), null) } returns
            Result.success(TrackPage(listOf(topTrack)))
        coEvery { youTubeRepository.searchArtists("odes", any()) } returns
            Result.success(listOf(odesza))
        coEvery { youTubeRepository.searchMusicSongs("ODESZA", any()) } returns
            Result.success(listOf(topTrack))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRecentQueryClick("odes")
        advanceUntilIdle()

        assertEquals("ODESZA", viewModel.uiState.value.featuredArtist?.name)
        assertEquals(listOf(topTrack), viewModel.uiState.value.artistTracks)
    }

    @Test
    fun `translit second pass runs when fewer than five results and deduplicates`() = runTest {
        stubDefaults(setOf(ServiceType.SOUNDCLOUD))
        val initial = track("sc-1", "Kino", "Gruppa Krovi", Source.SOUNDCLOUD)
        val translitTrack = track("sc-2", "Кино", "Перемен", Source.SOUNDCLOUD)
        coEvery { soundCloudRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { soundCloudRepository.searchPage("kino", any(), null) } returns
            Result.success(TrackPage(listOf(initial)))
        coEvery { soundCloudRepository.searchPage("кино", any(), null) } returns
            Result.success(TrackPage(listOf(initial, translitTrack)))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRecentQueryClick("kino")
        advanceUntilIdle()

        coVerify(exactly = 1) { soundCloudRepository.searchPage("кино", any(), null) }
        val ids = viewModel.uiState.value.results.map { it.id }
        assertEquals(listOf("sc-1", "sc-2"), ids)
    }

    @Test
    fun `translit second pass is skipped when five or more results found`() = runTest {
        stubDefaults(setOf(ServiceType.SOUNDCLOUD))
        val five = (1..5).map { track("sc-$it", "Artist", "Track $it", Source.SOUNDCLOUD) }
        coEvery { soundCloudRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { soundCloudRepository.searchPage("daft", any(), null) } returns
            Result.success(TrackPage(five))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRecentQueryClick("daft")
        advanceUntilIdle()

        coVerify(exactly = 0) { soundCloudRepository.searchPage("дафт", any(), null) }
        assertTrue(viewModel.uiState.value.results.size == 5)
    }

    private fun stubDefaults(sources: Set<ServiceType> = ServiceType.entries.toSet()) {
        every { statusManager.statuses } returns MutableStateFlow(
            ServiceType.entries.associateWith { ServiceHealth(isAvailable = true) },
        )
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { downloadRepository.observeDownloadedIds() } returns flowOf(emptySet())
        every { settingsRepository.settings } returns flowOf(AppSettings(searchSources = sources))
        coEvery { soundCloudRepository.searchPage(any(), any(), any()) } returns Result.success(TrackPage(emptyList()))
        coEvery { soundCloudRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { soundCloudRepository.searchUserTracks(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.searchPage(any(), any(), any()) } returns Result.success(TrackPage(emptyList()))
        coEvery { youTubeRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.searchMusicSongs(any(), any()) } returns Result.success(emptyList())
    }

    private fun createViewModel(): SearchViewModel = SearchViewModel(
        soundCloudRepository = soundCloudRepository,
        youTubeRepository = youTubeRepository,
        sourceRegistry = PlayableSourceRegistry(emptySet()),
        searchHistoryStore = mockk<SearchHistoryStore>(relaxed = true),
        serviceStatusManager = statusManager,
        connectivityObserver = connectivity,
        navigationEvents = NavigationEvents(),
        settingsRepository = settingsRepository,
        searchSuggestions = suggestions,
        downloadRepository = downloadRepository,
    )

    private fun track(id: String, artist: String, title: String, source: Source) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
    )
}
