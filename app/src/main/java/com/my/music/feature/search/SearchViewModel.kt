package com.my.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.service.ServiceHealth
import com.my.music.core.service.ServiceStatusManager
import com.my.music.core.service.ServiceType
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedService: ServiceType = ServiceType.SOUNDCLOUD,
)

/** Запрос поиска: источник входит в ключ, чтобы смена сервиса перезапускала поиск. */
private data class SearchRequest(
    val query: String,
    val service: ServiceType,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val youTubeRepository: YouTubeRepository,
    private val serviceStatusManager: ServiceStatusManager,
    downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
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

    init {
        // Без distinctUntilChanged: при быстром переключении источник может
        // вернуться к прежнему значению, и поиск всё равно должен перезапуститься.
        requestFlow
            .debounce(400L)
            .filter { it.query.length >= 2 } // минимум 2 символа
            .onEach { request -> startSearch(request) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
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
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedService = service,
                results = emptyList(),
                error = null,
                isLoading = query.length >= 2,
            )
        }
        requestFlow.update { it.copy(service = service) }
    }

    /** Повторный запуск поиска для кнопки «Повторить». */
    fun retry() {
        val current = _uiState.value
        if (current.query.length < 2) return
        startSearch(SearchRequest(query = current.query, service = current.selectedService))
    }

    /** Ручное обновление статусов из меню. */
    fun refreshStatuses() {
        serviceStatusManager.refreshStatus()
    }

    private fun startSearch(request: SearchRequest) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(request)
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val result = when (request.service) {
            ServiceType.SOUNDCLOUD -> soundCloudRepository.search(request.query)
            ServiceType.YOUTUBE -> youTubeRepository.search(request.query)
        }

        result
            .onSuccess { tracks ->
                serviceStatusManager.markAvailable(request.service)
                _uiState.update { state ->
                    if (state.matches(request)) {
                        state.copy(results = tracks, isLoading = false)
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
                // Ошибка поиска — повод перепроверить доступность сервисов
                serviceStatusManager.checkAllServices()
            }
    }

    private fun SearchUiState.matches(request: SearchRequest): Boolean =
        query == request.query && selectedService == request.service
}
