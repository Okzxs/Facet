package com.facet.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.navigation.SearchIntent
import com.facet.music.core.network.ConnectivityObserver
import com.facet.music.core.search.ArtistMerger
import com.facet.music.core.search.SearchHistoryStore
import com.facet.music.core.search.SearchSuggestions
import com.facet.music.core.search.TrackMerger
import com.facet.music.core.search.Translit
import com.facet.music.core.service.ServiceHealth
import com.facet.music.core.service.ServiceStatusManager
import com.facet.music.core.service.ServiceType
import com.facet.music.core.source.PlayableSourceRegistry
import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val featuredArtist: Artist? = null,
    val artistTracks: List<Track> = emptyList(),
    val artistTracksLoading: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedSources: Set<ServiceType> = ServiceType.entries.toSet(),
    val recentQueries: List<String> = emptyList(),
    val isOffline: Boolean = false,
    val sourceHint: String? = null,
    val suggestions: List<String> = emptyList(),
)

private data class SearchRequest(
    val query: String,
    val sources: Set<ServiceType>,
)

private sealed interface SourceEvent {
    val service: ServiceType

    data class Tracks(
        override val service: ServiceType,
        val page: TrackPage,
    ) : SourceEvent

    data class Artists(
        override val service: ServiceType,
        val artists: List<Artist>,
    ) : SourceEvent

    data class Failed(override val service: ServiceType) : SourceEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val youTubeRepository: YouTubeRepository,
    private val sourceRegistry: PlayableSourceRegistry,
    private val searchHistoryStore: SearchHistoryStore,
    private val serviceStatusManager: ServiceStatusManager,
    private val connectivityObserver: ConnectivityObserver,
    private val navigationEvents: NavigationEvents,
    private val settingsRepository: SettingsRepository,
    private val searchSuggestions: SearchSuggestions,
    downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(recentQueries = searchHistoryStore.recent()),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val serviceStatuses: StateFlow<Map<ServiceType, ServiceHealth>> =
        serviceStatusManager.statuses

    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    private val requestFlow = MutableStateFlow(
        SearchRequest("", ServiceType.entries.toSet()),
    )

    private var searchJob: Job? = null
    private var translitJob: Job? = null
    private var artistTracksJob: Job? = null
    private var suggestionJob: Job? = null

    private var directRequest: SearchRequest? = null
    private var currentRequest: SearchRequest? = null
    private val nextPages = java.util.concurrent.ConcurrentHashMap<ServiceType, String>()

    init {
        serviceStatusManager.ensureFresh()

        viewModelScope.launch {
            val sources = settingsRepository.settings.first().searchSources
                .ifEmpty { ServiceType.entries.toSet() }
            if (_uiState.value.query.isBlank()) {
                _uiState.update { it.copy(selectedSources = sources) }
                requestFlow.update { it.copy(sources = sources) }
            }
        }

        viewModelScope.launch {
            var wasOffline = !connectivityObserver.isOnline.value
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
                if (online && wasOffline && _uiState.value.error == OFFLINE_MESSAGE) {
                    retry()
                }
                wasOffline = !online
            }
        }

        requestFlow
            .debounce(400L)
            .filter { it.query.length >= 2 }
            .onEach { request ->
                if (request == directRequest) {
                    directRequest = null
                } else {
                    startSearch(request)
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            navigationEvents.searchIntents.collect { intent ->
                navigationEvents.consumeSearchIntent()
                openExternalSearch(intent)
            }
        }
    }

    fun onQueryChange(query: String) {
        directRequest = null
        _uiState.update {
            it.copy(
                query = query,
                error = null,
                sourceHint = null,
                suggestions = if (query.length < 2) emptyList() else it.suggestions,
            )
        }
        requestFlow.update { it.copy(query = query) }
        requestSuggestions(query)

        if (query.length < 2) {
            searchJob?.cancel()
            translitJob?.cancel()
            artistTracksJob?.cancel()
            nextPages.clear()
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    artists = emptyList(),
                    featuredArtist = null,
                    artistTracks = emptyList(),
                    artistTracksLoading = false,
                    isLoading = false,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun onSourceToggle(service: ServiceType) {
        val current = _uiState.value.selectedSources
        val next = if (service in current) current - service else current + service
        if (next.isEmpty()) {
            _uiState.update { it.copy(sourceHint = SOURCE_HINT) }
            return
        }
        applySources(next)
    }

    fun onAllSourcesSelected() {
        if (_uiState.value.selectedSources.size == ServiceType.entries.size) return
        applySources(ServiceType.entries.toSet())
    }

    private fun applySources(sources: Set<ServiceType>) {
        val query = _uiState.value.query
        directRequest = null
        searchJob?.cancel()
        translitJob?.cancel()
        artistTracksJob?.cancel()
        nextPages.clear()
        _uiState.update {
            it.copy(
                selectedSources = sources,
                results = emptyList(),
                artists = emptyList(),
                featuredArtist = null,
                artistTracks = emptyList(),
                artistTracksLoading = false,
                error = null,
                sourceHint = null,
                isLoading = query.length >= 2,
            )
        }
        requestFlow.update { it.copy(sources = sources) }
        viewModelScope.launch { settingsRepository.setSearchSources(sources) }
    }

    fun retry() {
        val current = _uiState.value
        if (current.query.length < 2) return
        startSearch(SearchRequest(current.query, current.selectedSources))
    }

    fun onRecentQueryClick(query: String) {
        startImmediate(query)
    }

    fun onSuggestionSelected(query: String) {
        startImmediate(query)
    }

    private fun startImmediate(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        directRequest = null
        searchJob?.cancel()
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                query = trimmed,
                results = emptyList(),
                artists = emptyList(),
                featuredArtist = null,
                artistTracks = emptyList(),
                error = null,
                suggestions = emptyList(),
                isLoading = true,
            )
        }
        val request = SearchRequest(trimmed, _uiState.value.selectedSources)
        directRequest = request
        requestFlow.value = request
        startSearch(request)
    }

    fun clearHistory() {
        searchHistoryStore.clear()
        _uiState.update { it.copy(recentQueries = emptyList()) }
    }

    fun refreshStatuses() {
        serviceStatusManager.refreshStatus()
    }

    fun onStatusPanelOpened() {
        serviceStatusManager.ensureFresh()
    }

    private fun openExternalSearch(intent: SearchIntent) {
        val query = intent.query.trim()
        val sources = setOf(intent.service)
        val request = SearchRequest(query, sources)
        directRequest = request
        searchJob?.cancel()
        requestFlow.value = request
        _uiState.update {
            it.copy(
                query = query,
                selectedSources = sources,
                results = emptyList(),
                artists = emptyList(),
                featuredArtist = null,
                artistTracks = emptyList(),
                error = null,
                suggestions = emptyList(),
                isLoading = query.length >= 2,
            )
        }
        viewModelScope.launch { settingsRepository.setSearchSources(sources) }
        if (query.length >= 2) startSearch(request)
    }

    private fun startSearch(request: SearchRequest) {
        searchJob?.cancel()
        translitJob?.cancel()
        artistTracksJob?.cancel()
        if (!connectivityObserver.isOnline.value) {
            _uiState.update { state ->
                if (state.matches(request)) {
                    state.copy(
                        isLoading = false,
                        error = OFFLINE_MESSAGE,
                        results = emptyList(),
                        artists = emptyList(),
                    )
                } else {
                    state
                }
            }
            return
        }
        searchJob = viewModelScope.launch {
            performSearch(request)
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        currentRequest = request
        nextPages.clear()
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                results = emptyList(),
                artists = emptyList(),
                featuredArtist = null,
                artistTracks = emptyList(),
                artistTracksLoading = false,
            )
        }
        performSourcesSearch(request)
    }

    private suspend fun performSourcesSearch(request: SearchRequest) {
        val groups = LinkedHashMap<ServiceType, List<Track>>()
        val artistGroups = LinkedHashMap<ServiceType, List<Artist>>()
        val sources = orderedSources(request.sources)
        var displayed = emptyList<Track>()
        var displayedArtists = emptyList<Artist>()
        var completed = 0
        var successes = 0
        val total = sources.size * 2

        channelFlow {
            sources.forEach { service ->
                launch {
                    val tracks = safeResult {
                        searchPageInSource(service, request.query, token = null)
                    }
                    send(
                        tracks.fold(
                            onSuccess = { SourceEvent.Tracks(service, it) },
                            onFailure = { SourceEvent.Failed(service) },
                        ),
                    )
                }
                launch {
                    val artists = safeResult { searchArtistsInSource(service, request.query) }
                    send(
                        artists.fold(
                            onSuccess = { SourceEvent.Artists(service, it) },
                            onFailure = { SourceEvent.Failed(service) },
                        ),
                    )
                }
            }
        }.collect { event ->
            completed++
            when (event) {
                is SourceEvent.Tracks -> {
                    successes++
                    groups[event.service] = event.page.tracks
                    val next = event.page.nextToken
                    if (next != null) {
                        nextPages[event.service] = next
                    } else {
                        nextPages.remove(event.service)
                    }
                    serviceStatusManager.markAvailable(event.service)
                    displayed = TrackMerger.mergeKeepingOrder(
                        displayed,
                        groups.values.toList(),
                        request.query,
                    )
                }
                is SourceEvent.Artists -> {
                    successes++
                    artistGroups[event.service] = event.artists
                    displayedArtists = ArtistMerger.merge(
                        artistGroups.values.toList(),
                        request.query,
                    ).take(ARTIST_LIMIT)
                }
                is SourceEvent.Failed -> Unit
            }

            if (displayed.isNotEmpty() || displayedArtists.isNotEmpty()) {
                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(
                            results = displayed,
                            artists = displayedArtists,
                            isLoading = false,
                            error = null,
                        )
                    } else {
                        state
                    }
                }
            }

            if (completed == total) {
                searchHistoryStore.add(request.query)
                val featured = ArtistMerger.bestMatch(request.query, displayedArtists)
                val error = if (displayed.isEmpty() && displayedArtists.isEmpty() && successes == 0) {
                    ALL_SOURCES_FAILED_MESSAGE
                } else {
                    null
                }
                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(
                            results = displayed,
                            artists = displayedArtists,
                            featuredArtist = featured,
                            artistTracks = emptyList(),
                            artistTracksLoading = featured != null,
                            isLoading = false,
                            error = error,
                            recentQueries = searchHistoryStore.recent(),
                        )
                    } else {
                        state
                    }
                }
                if (featured != null) {
                    loadArtistTracks(request, featured)
                }
                maybeSearchTranslit(request, displayed)
            }
        }
    }

    private suspend fun searchPageInSource(
        service: ServiceType,
        query: String,
        token: String?,
    ): Result<TrackPage> {
        sourceRegistry.provider(service.source)?.let { provider ->
            return provider.searchPage(
                query = query,
                limit = PAGE_SIZE,
                nextToken = token,
                useCache = true,
            )
        }
        return when (service) {
            ServiceType.SOUNDCLOUD -> soundCloudRepository.searchPage(query, PAGE_SIZE, token)
            ServiceType.YOUTUBE -> youTubeRepository.searchPage(query, PAGE_SIZE, token)
            ServiceType.SPOTIFY -> Result.failure(
                IllegalStateException("Источник ${service.displayName} не подключён"),
            )
        }
    }

    private suspend fun searchArtistsInSource(
        service: ServiceType,
        query: String,
    ): Result<List<Artist>> {
        sourceRegistry.provider(service.source)?.let { provider ->
            return provider.searchArtists(query, ARTIST_LIMIT)
        }
        return when (service) {
            ServiceType.SOUNDCLOUD -> soundCloudRepository.searchArtists(query, ARTIST_LIMIT)
            ServiceType.YOUTUBE -> youTubeRepository.searchArtists(query, ARTIST_LIMIT)
            ServiceType.SPOTIFY -> Result.success(emptyList())
        }
    }

    private suspend fun <T> safeResult(block: suspend () -> Result<T>): Result<T> =
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        } ?: Result.failure(IOException("timeout"))

    private fun loadArtistTracks(request: SearchRequest, artist: Artist) {
        artistTracksJob?.cancel()
        artistTracksJob = viewModelScope.launch {
            val tracks = withTimeoutOrNull(ARTIST_TRACKS_TIMEOUT_MS) {
                when (artist.source) {
                    Source.YOUTUBE -> youTubeRepository.searchMusicSongs(artist.name, 20)
                        .getOrNull()
                        .orEmpty()
                        .filter { track ->
                            val name = ArtistMerger.normalize(artist.name)
                            val value = ArtistMerger.normalize(track.artist)
                            value.contains(name) || name.contains(value)
                        }
                    Source.SOUNDCLOUD -> soundCloudRepository
                        .searchUserTracks(artist.url, 20)
                        .getOrNull()
                        .orEmpty()
                    else -> emptyList()
                }
            }.orEmpty().take(ARTIST_TRACK_LIMIT)

            _uiState.update { state ->
                if (state.matches(request) && state.featuredArtist?.id == artist.id) {
                    state.copy(artistTracks = tracks, artistTracksLoading = false)
                } else {
                    state
                }
            }
        }
    }

    private fun maybeSearchTranslit(request: SearchRequest, displayed: List<Track>) {
        if (displayed.size >= TRANSLIT_THRESHOLD) return
        val alternate = Translit.alternate(request.query) ?: return
        translitJob?.cancel()
        translitJob = viewModelScope.launch {
            val groups = orderedSources(request.sources).map { service ->
                async { safeResult { searchPageInSource(service, alternate, null) } }
            }.awaitAll()
                .mapNotNull { it.getOrNull()?.tracks }
            if (groups.isEmpty()) return@launch
            val extra = TrackMerger.merge(groups, request.query)
            _uiState.update { state ->
                if (!state.matches(request)) return@update state
                val existing = state.results.map { TrackMerger.key(it) }.toSet()
                val additions = extra.filter { TrackMerger.key(it) !in existing }
                if (additions.isEmpty()) {
                    state
                } else {
                    state.copy(
                        results = state.results + additions,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val request = currentRequest ?: return
        if (_uiState.value.isLoadingMore) return
        val tokens = orderedSources(nextPages.keys).filter { nextPages.containsKey(it) }
        if (tokens.isEmpty()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val additions = mutableListOf<Track>()
            channelFlow {
                tokens.forEach { service ->
                    launch {
                        val token = nextPages[service] ?: return@launch
                        val page = safeResult {
                            searchPageInSource(service, request.query, token)
                        }.getOrNull()
                        if (page != null) {
                            if (page.nextToken != null) {
                                nextPages[service] = page.nextToken
                            } else {
                                nextPages.remove(service)
                            }
                            send(service to page.tracks)
                        }
                    }
                }
            }.collect { (service, tracks) ->
                if (tracks.isNotEmpty()) {
                    serviceStatusManager.markAvailable(service)
                    additions += tracks
                }
            }
            _uiState.update { state ->
                if (!state.matches(request)) return@update state
                val merged = TrackMerger.mergeKeepingOrder(
                    existing = state.results,
                    groups = listOf(state.results, additions),
                    query = request.query,
                )
                state.copy(results = merged, isLoadingMore = false)
            }
        }
    }

    private fun requestSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val suggestions = runCatching { searchSuggestions.suggest(query) }.getOrDefault(emptyList())
            _uiState.update { state ->
                if (state.query == query) state.copy(suggestions = suggestions) else state
            }
        }
    }

    private fun orderedSources(sources: Set<ServiceType>): List<ServiceType> =
        ServiceType.entries.filter { it in sources }

    private fun SearchUiState.matches(request: SearchRequest): Boolean =
        query == request.query && selectedSources == request.sources

    private companion object {
        const val SOURCE_TIMEOUT_MS = 6_000L
        const val ARTIST_TRACKS_TIMEOUT_MS = 5_000L
        const val ARTIST_LIMIT = 10
        const val ARTIST_TRACK_LIMIT = 10
        const val PAGE_SIZE = 20
        const val TRANSLIT_THRESHOLD = 5
        const val SUGGEST_DEBOUNCE_MS = 250L
        const val OFFLINE_MESSAGE = "Нет подключения к интернету"
        const val ALL_SOURCES_FAILED_MESSAGE = "Все источники недоступны. Проверьте подключение."
        const val SOURCE_HINT = "Нужен хотя бы один источник"
    }
}
