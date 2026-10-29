package com.my.music.feature.search

import com.my.music.core.navigation.NavigationEvents
import com.my.music.core.service.ServiceHealth
import com.my.music.core.search.SearchHistoryStore
import com.my.music.core.service.ServiceStatusManager
import com.my.music.core.source.MusicSourceRegistry
import com.my.music.core.service.ServiceType
import com.my.music.data.repository.DownloadRepository
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
import org.junit.Rule
import org.junit.Test

class SearchViewModelExternalRequestTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val youTubeRepository = mockk<YouTubeRepository>()
    private val statusManager = mockk<ServiceStatusManager>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>()

    @Test
    fun `external intent selects youtube and searches immediately`() {
        stubDependencies()
        coEvery { soundCloudRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.search(any(), any()) } returns Result.success(emptyList())

        val events = NavigationEvents()
        events.requestSearch(query = "Artist - Title", service = ServiceType.YOUTUBE)

        val viewModel = createViewModel(events)

        assertEquals("Artist - Title", viewModel.uiState.value.query)
        assertEquals(ServiceType.YOUTUBE, viewModel.uiState.value.selectedService)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { youTubeRepository.search("Artist - Title", 20) }
        coVerify(exactly = 0) { soundCloudRepository.search(any(), any()) }
    }

    @Test
    fun `external intent is consumed and not repeated by a new screen instance`() {
        stubDependencies()
        coEvery { soundCloudRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { youTubeRepository.search(any(), any()) } returns Result.success(emptyList())

        val events = NavigationEvents()
        events.requestSearch(query = "Artist - Title", service = ServiceType.YOUTUBE)
        createViewModel(events)

        // Имитируем повторное открытие экрана: replay-событие уже погашено.
        createViewModel(events)

        coVerify(exactly = 1) { youTubeRepository.search("Artist - Title", 20) }
    }

    private fun stubDependencies() {
        every { statusManager.statuses } returns MutableStateFlow(
            ServiceType.entries.associateWith { ServiceHealth(isAvailable = true) },
        )
        every { downloadRepository.observeDownloadedIds() } returns flowOf(emptySet())
    }

    private fun createViewModel(events: NavigationEvents): SearchViewModel = SearchViewModel(
        soundCloudRepository = soundCloudRepository,
        youTubeRepository = youTubeRepository,
        sourceRegistry = MusicSourceRegistry(emptySet()),
        searchHistoryStore = mockk(relaxed = true),
        serviceStatusManager = statusManager,
        navigationEvents = events,
        downloadRepository = downloadRepository,
    )
}
