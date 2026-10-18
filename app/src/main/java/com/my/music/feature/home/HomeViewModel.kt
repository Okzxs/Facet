package com.my.music.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreSection(
    val genre: String,
    val tracks: List<Track>,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val newAndHot: List<Track> = emptyList(),
    val top50: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val genreSections: List<GenreSection> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chartsRepository: SoundCloudChartsRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
) : ViewModel() {

    private val tag = "HomeViewModel"

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeLocalData()
        loadCharts(forceRefresh = false)
    }

    /**
     * Непрерывное наблюдение за локальными данными Room:
     * - Кэш чартов (New & hot, Top 50)
     * - Недавние треки (Play history)
     * - Лайкнутые треки (Liked tracks)
     * - Динамические жанровые подборки
     */
    private fun observeLocalData() {
        // Наблюдение за кэшем чартов
        viewModelScope.launch {
            chartsRepository.observeNewAndHot().collect { tracks ->
                _uiState.update { it.copy(newAndHot = tracks) }
            }
        }

        viewModelScope.launch {
            chartsRepository.observeTop50().collect { tracks ->
                _uiState.update { it.copy(top50 = tracks) }
            }
        }

        // Наблюдение за личными данными (история и лайки) и группировка по жанрам
        viewModelScope.launch {
            combine(
                playHistoryRepository.getPlayHistory(),
                likedTracksRepository.getLikedTracks(),
            ) { history: List<Track>, liked: List<Track> ->
                val recent = history.take(20)
                val allPersonal = (recent + liked).distinctBy { it.id }
                val genres = allPersonal
                    .filter { !it.genre.isNullOrBlank() }
                    .groupBy { it.genre!!.trim() }
                    .map { (genre, tracks) -> GenreSection(genre = genre, tracks = tracks) }
                    .sortedByDescending { it.tracks.size }

                Triple(recent, liked, genres)
            }.collect { (recent, liked, genres) ->
                _uiState.update {
                    it.copy(
                        recentTracks = recent,
                        likedTracks = liked,
                        genreSections = genres,
                    )
                }
            }
        }
    }

    /**
     * Загрузка чартов из репозитория (кэш Room / сеть NewPipe).
     */
    fun loadCharts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else if (_uiState.value.newAndHot.isEmpty() && _uiState.value.top50.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val newHotDeferred = async { chartsRepository.getNewAndHot(forceRefresh) }
                val top50Deferred = async { chartsRepository.getTop50(forceRefresh) }

                val newHotResult = newHotDeferred.await()
                val top50Result = top50Deferred.await()

                val errorMessage = when {
                    newHotResult.isFailure && top50Result.isFailure ->
                        newHotResult.exceptionOrNull()?.message ?: "Не удалось загрузить чарты"
                    else -> null
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = errorMessage,
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Ошибка при обновлении чартов: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Ошибка загрузки",
                    )
                }
            }
        }
    }

    /**
     * Принудительное обновление для Pull-to-refresh.
     */
    fun refresh() {
        loadCharts(forceRefresh = true)
    }
}
