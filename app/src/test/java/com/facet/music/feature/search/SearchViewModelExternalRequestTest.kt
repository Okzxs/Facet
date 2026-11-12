package com.facet.music.feature.search

import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.network.ConnectivityObserver
import com.facet.music.core.search.SearchSuggestions
import com.facet.music.core.service.ServiceHealth
import com.facet.music.core.search.SearchHistoryStore
import com.facet.music.core.service.ServiceStatusManager
import com.facet.music.core.source.PlayableSourceRegistry
import com.facet.music.core.service.ServiceType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SearchViewModelExternalRequestTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val youTubeRepository = mockk<YouTubeRepository>()
    private val statusManager = mockk<ServiceStatusManager>(relaxed = true)
    private val connectivity = mockk<ConnectivityObserver>()
    private val downloadRepository = mockk<DownloadRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val suggestions = mockk<SearchSuggestions>(relaxed = true)

    @Test
    fun `external intent selects youtube and searches immediately`() {
        stubDependencies()
        coEvery { soundCloudRepository.searchPage(any(), any(), any()) } returns
            Result.success(TrackPage(emptyList()))
        coEvery { youTubeRepository.searchPage(any(), any(), any()) } returns
            Result.success(TrackPage(emptyList()))
        coEvery { soundCloudRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.searchArtists(any(), any()) } returns Result.success(emptyList())

        val events = NavigationEvents()
        events.requestSearch(query = "Artist - Title", service = ServiceType.YOUTUBE)

        val viewModel = createViewModel(events)

        assertEquals("Artist - Title", viewModel.uiState.value.query)
        assertEquals(setOf(ServiceType.YOUTUBE), viewModel.uiState.value.selectedSources)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { youTubeRepository.searchPage("Artist - Title", any(), null) }
        coVerify(exactly = 0) { soundCloudRepository.searchPage(any(), any(), any()) }
    }

    @Test
    fun `external intent is consumed and not repeated by a new screen instance`() {
        stubDependencies()
        coEvery { soundCloudRepository.searchPage(any(), any(), any()) } returns
            Result.success(TrackPage(emptyList()))
        coEvery { youTubeRepository.searchPage(any(), any(), any()) } returns
            Result.success(TrackPage(emptyList()))
        coEvery { soundCloudRepository.searchArtists(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.searchArtists(any(), any()) } returns Result.success(emptyList())

        val events = NavigationEvents()
        events.requestSearch(query = "Artist - Title", service = ServiceType.YOUTUBE)
        createViewModel(events)

        createViewModel(events)

        coVerify(exactly = 1) { youTubeRepository.searchPage("Artist - Title", any(), null) }
    }

    private fun stubDependencies() {
        every { statusManager.statuses } returns MutableStateFlow(
            ServiceType.entries.associateWith { ServiceHealth(isAvailable = true) },
        )
        every { connectivity.isOnline } returns MutableStateFlow(true)
        every { downloadRepository.observeDownloadedIds() } returns flowOf(emptySet())
        every { settingsRepository.settings } returns flowOf(AppSettings())
    }

    private fun createViewModel(events: NavigationEvents): SearchViewModel = SearchViewModel(
        soundCloudRepository = soundCloudRepository,
        youTubeRepository = youTubeRepository,
        sourceRegistry = PlayableSourceRegistry(emptySet()),
        searchHistoryStore = mockk(relaxed = true),
        serviceStatusManager = statusManager,
        connectivityObserver = connectivity,
        navigationEvents = events,
        settingsRepository = settingsRepository,
        searchSuggestions = suggestions,
        downloadRepository = downloadRepository,
    )
}
