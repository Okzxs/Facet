package com.my.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.SoundCloudRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SoundCloudRepository,
    downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Идентификаторы скачанных треков — для бейджей «офлайн» в результатах. */
    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    // Поток запросов: debounce 400ms чтобы не спамить API при наборе текста
    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            .debounce(400L)
            .distinctUntilChanged()
            .filter { it.length >= 2 } // минимум 2 символа
            .onEach { query -> performSearch(query) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        queryFlow.value = query

        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        repository.search(query)
            .onSuccess { tracks ->
                _uiState.update {
                    it.copy(results = tracks, isLoading = false)
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Ошибка поиска: ${error.message}",
                    )
                }
            }
    }
}
