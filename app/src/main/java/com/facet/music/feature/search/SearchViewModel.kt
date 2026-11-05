package com.facet.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.navigation.SearchIntent
import com.facet.music.core.network.ConnectivityObserver
import com.facet.music.core.search.SearchHistoryStore
import com.facet.music.core.service.ServiceHealth
import com.facet.music.core.service.ServiceStatusManager
import com.facet.music.core.search.TrackMerger
import com.facet.music.core.service.ServiceType
import com.facet.music.core.source.PlayableSourceRegistry
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedService: ServiceType = ServiceType.SOUNDCLOUD,
    /** Сводный поиск: параллельно по всем источникам с дедупом и бейджами «+N». */
    val allSources: Boolean = false,
    /** Последние запросы — показываются чипсами, пока поле пустое. */
    val recentQueries: List<String> = emptyList(),
    /** Нет сети: вместо шторма ошибок показываем баннер. */
    val isOffline: Boolean = false,
)

/** Запрос поиска: источник входит в ключ, чтобы смена сервиса перезапускала поиск. */
private data class SearchRequest(
    val query: String,
    val service: ServiceType,
    val allSources: Boolean = false,
)

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
    downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(recentQueries = searchHistoryStore.recent()),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Глобальные статусы сервисов из Singleton-менеджера. */
    val serviceStatuses: StateFlow<Map<ServiceType, ServiceHealth>> =
        serviceStatusManager.statuses

    /** Идентификаторы скачанных треков — для бейджей «офлайн» в результатах. */
    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    // Поток запросов: debounce 400ms чтобы не спамить API при наборе текста
    private val requestFlow = MutableStateFlow(SearchRequest("", ServiceType.SOUNDCLOUD))

    private var searchJob: Job? = null

    /**
     * Запрос извне уже выполняется напрямую. Его дубль из debounce-потока
     * нужно пропустить, иначе YouTube получит два одинаковых запроса.
     */
    private var directRequest: SearchRequest? = null

    init {
        serviceStatusManager.ensureFresh()

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

        // Без distinctUntilChanged: при быстром переключении источник может
        // вернуться к прежнему значению, и поиск всё равно должен перезапуститься.
        requestFlow
            .debounce(400L)
            .filter { it.query.length >= 2 } // минимум 2 символа
            .onEach { request ->
                if (request == directRequest) {
                    directRequest = null
                } else {
                    startSearch(request)
                }
            }
            .launchIn(viewModelScope)

        // Намерение из плеера: поиск должен выполниться сразу, без debounce.
        viewModelScope.launch {
            navigationEvents.searchIntents.collect { intent ->
                navigationEvents.consumeSearchIntent()
                openExternalSearch(intent)
            }
        }
    }

    fun onQueryChange(query: String) {
        directRequest = null
        _uiState.update { it.copy(query = query, error = null) }
        requestFlow.update { it.copy(query = query) }

        if (query.length < 2) {
            searchJob?.cancel()
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }

    /**
     * Переключение источника. Результаты старого сервиса сразу убираем,
     * чтобы пользователь не принял их за результаты нового.
     */
    fun onServiceSelected(service: ServiceType) {
        if (_uiState.value.selectedService == service) return

        val query = _uiState.value.query
        directRequest = null
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedService = service,
                allSources = false,
                results = emptyList(),
                error = null,
                isLoading = query.length >= 2,
            )
        }
        requestFlow.update { it.copy(service = service, allSources = false) }
    }

    /**
     * Сводный поиск по всем источникам: тот же запрос уходит параллельно
     * в SoundCloud, YouTube и Spotify, результаты сливаются.
     */
    fun onAllSourcesSelected() {
        if (_uiState.value.allSources) return

        val query = _uiState.value.query
        directRequest = null
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                allSources = true,
                results = emptyList(),
                error = null,
                isLoading = query.length >= 2,
            )
        }
        requestFlow.update { it.copy(allSources = true) }
    }

    /** Повторный запуск поиска для кнопки «Повторить». */
    fun retry() {
        val current = _uiState.value
        if (current.query.length < 2) return
        startSearch(
            SearchRequest(
                query = current.query,
                service = current.selectedService,
                allSources = current.allSources,
            ),
        )
    }

    /**
     * Поиск в выбранном источнике: сначала провайдер из реестра (Spotify),
     * затем репозитории для исторических источников (SoundCloud, YouTube).
     */
    private suspend fun searchInSource(service: ServiceType, query: String): Result<List<Track>> {
        sourceRegistry.provider(service.source)?.let { provider ->
            return provider.search(query = query)
        }
        return when (service) {
            ServiceType.SOUNDCLOUD -> soundCloudRepository.search(query)
            ServiceType.YOUTUBE -> youTubeRepository.search(query)
            ServiceType.SPOTIFY -> Result.failure(
                IllegalStateException("Источник ${service.displayName} не подключён"),
            )
        }
    }

    /** Тап по чипсу недавнего запроса: подставляем текст и сразу ищем. */
    fun onRecentQueryClick(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        directRequest = null
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = trimmed, results = emptyList(), error = null, isLoading = true)
        }
        startSearch(
            SearchRequest(
                query = trimmed,
                service = _uiState.value.selectedService,
                allSources = _uiState.value.allSources,
            ),
        )
    }

    /** Очистка истории запросов (кнопка рядом с чипсами). */
    fun clearHistory() {
        searchHistoryStore.clear()
        _uiState.update { it.copy(recentQueries = emptyList()) }
    }

    /** Ручное обновление статусов из меню. */
    fun refreshStatuses() {
        serviceStatusManager.refreshStatus()
    }

    /**
     * Внешнее намерение из плеера: переключаем источник, подставляем запрос
     * и запускаем поиск немедленно, не дожидаясь 400 мс.
     */
    private fun openExternalSearch(intent: SearchIntent) {
        val query = intent.query.trim()
        val request = SearchRequest(query = query, service = intent.service)

        directRequest = request
        searchJob?.cancel()
        requestFlow.value = request

        _uiState.update {
            it.copy(
                query = query,
                selectedService = intent.service,
                results = emptyList(),
                error = null,
                isLoading = query.length >= 2,
            )
        }

        if (query.length >= 2) {
            startSearch(request)
        }
    }

    private fun startSearch(request: SearchRequest) {
        searchJob?.cancel()
        if (!connectivityObserver.isOnline.value) {
            _uiState.update { state ->
                if (state.matches(request)) {
                    state.copy(isLoading = false, error = OFFLINE_MESSAGE, results = emptyList())
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
        _uiState.update { it.copy(isLoading = true, error = null) }

        if (request.allSources) {
            performAllSourcesSearch(request)
            return
        }

        searchInSource(request.service, request.query)
            .onSuccess { tracks ->
                serviceStatusManager.markAvailable(request.service)
                searchHistoryStore.add(request.query)
                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(
                            results = tracks,
                            isLoading = false,
                            recentQueries = searchHistoryStore.recent(),
                        )
                    } else {
                        state
                    }
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error

                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(
                            isLoading = false,
                            error = "${request.service.displayName}: " +
                                (error.message ?: "неизвестная ошибка"),
                        )
                    } else {
                        state
                    }
                }
            }
    }

    /**
     * Сводный поиск отдаёт источники по мере ответа: первый непустой
     * результат сразу на экране, следующие вливаются через TrackMerger без
     * перестановки уже показанных строк (иначе прыгает скролл). Каждый
     * источник ограничен 6 секундами, ошибка одного не рушит выдачу.
     */
    private suspend fun performAllSourcesSearch(request: SearchRequest) {
        val groups = LinkedHashMap<ServiceType, List<Track>>()
        var displayed = emptyList<Track>()
        var completed = 0
        var successes = 0
        val total = ServiceType.entries.size

        channelFlow {
            ServiceType.entries.forEach { service ->
                launch {
                    val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        try {
                            searchInSource(service, request.query)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    } ?: Result.failure(IOException("timeout"))
                    send(service to result)
                }
            }
        }.collect { (service, result) ->
            completed++
            val tracks = result.getOrNull()
            if (tracks != null) {
                successes++
                groups[service] = tracks
                serviceStatusManager.markAvailable(service)
                displayed = TrackMerger.mergeKeepingOrder(displayed, groups.values.toList())
                if (displayed.isNotEmpty()) {
                    _uiState.update { state ->
                        if (state.matches(request)) {
                            state.copy(results = displayed, isLoading = false, error = null)
                        } else {
                            state
                        }
                    }
                }
            }

            if (completed == total) {
                searchHistoryStore.add(request.query)
                val error = if (displayed.isEmpty() && successes == 0) {
                    ALL_SOURCES_FAILED_MESSAGE
                } else {
                    null
                }
                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(
                            results = displayed,
                            isLoading = false,
                            error = error,
                            recentQueries = searchHistoryStore.recent(),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Открытие панели статусов: проверка не чаще раза в 10 минут. */
    fun onStatusPanelOpened() {
        serviceStatusManager.ensureFresh()
    }

    private fun SearchUiState.matches(request: SearchRequest): Boolean =
        query == request.query &&
            selectedService == request.service &&
            allSources == request.allSources

    private companion object {
        const val SOURCE_TIMEOUT_MS = 6_000L
        const val OFFLINE_MESSAGE = "Нет подключения к интернету"
        const val ALL_SOURCES_FAILED_MESSAGE = "Все источники недоступны. Проверьте подключение."
    }
}
