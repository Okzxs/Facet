package com.my.music.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.util.SeasonProvider
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val isRefreshing: Boolean = false,
    val isChartsLoading: Boolean = true,
    val chartsError: String? = null,
    val topRu: List<Track> = emptyList(),
    val topUs: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val memories: List<SeasonMemory> = emptyList(),
    val memorySeason: String = SeasonProvider.currentSeason(),
    val genreSections: List<GenreSection> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
) : ViewModel() {

    private val tag = "HomeViewModel"
    private val memorySeason = SeasonProvider.currentSeason()

    private val _uiState = MutableStateFlow(HomeUiState(memorySeason = memorySeason))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var chartsJob: Job? = null

    init {
        observeLocalData()
        loadCharts(forceRefresh = false)
    }

    /**
     * Локальные данные Room: история, лайки, воспоминания и жанровые подборки.
     */
    private fun observeLocalData() {
        viewModelScope.launch {
            likedTracksRepository.getSeasonMemories(memorySeason).collect { memories ->
                _uiState.update { it.copy(memories = memories) }
            }
        }

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
     * Чарты Top RU / Top US через YouTubeRepository.
     * При ошибке уже загруженные данные не затираются.
     */
    fun loadCharts(forceRefresh: Boolean = false) {
        if (chartsJob?.isActive == true) return

        chartsJob = viewModelScope.launch {
            val hasCharts = _uiState.value.topRu.isNotEmpty() || _uiState.value.topUs.isNotEmpty()
            _uiState.update {
                it.copy(
                    isRefreshing = forceRefresh,
                    isChartsLoading = !hasCharts,
                    chartsError = if (forceRefresh || !hasCharts) null else it.chartsError,
                )
            }

            try {
                val (ruResult, usResult) = coroutineScope {
                    val ruDeferred = async {
                        youtubeRepository.search(TOP_RU_QUERY, CHART_LIMIT)
                    }
                    val usDeferred = async {
                        youtubeRepository.search(TOP_US_QUERY, CHART_LIMIT)
                    }
                    ruDeferred.await() to usDeferred.await()
                }

                val ruTracks = ruResult.getOrNull().orEmpty().asChartTracks()
                val usTracks = usResult.getOrNull().orEmpty().asChartTracks()

                _uiState.update { state ->
                    val topRu = if (ruResult.isSuccess) ruTracks else state.topRu
                    val topUs = if (usResult.isSuccess) usTracks else state.topUs
                    val ruMissing = ruResult.isFailure && topRu.isEmpty()
                    val usMissing = usResult.isFailure && topUs.isEmpty()
                    val error = when {
                        ruMissing && usMissing -> "Не удалось загрузить чарты"
                        ruMissing -> "Не удалось загрузить Top RU"
                        usMissing -> "Не удалось загрузить Top US"
                        else -> null
                    }

                    state.copy(
                        topRu = topRu,
                        topUs = topUs,
                        chartsError = error,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Ошибка загрузки чартов YouTube: ${e.message}", e)
                _uiState.update {
                    it.copy(chartsError = e.message ?: "Ошибка загрузки чартов")
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isChartsLoading = false,
                    )
                }
            }
        }
    }

    private fun List<Track>.asChartTracks(): List<Track> {
        val music = filter { it.durationMs in MUSIC_MIN_DURATION_MS..MUSIC_MAX_DURATION_MS }
        return music.ifEmpty { this }
            .distinctBy { it.id }
            .take(CHART_TRACKS_LIMIT)
    }

    /** Принудительное обновление для Pull-to-refresh. */
    fun refresh() {
        loadCharts(forceRefresh = true)
    }

    private companion object {
        const val TOP_RU_QUERY = "Top 100 Russia music"
        const val TOP_US_QUERY = "Top 100 USA music"
        const val CHART_LIMIT = 8
        const val CHART_TRACKS_LIMIT = 5
        const val MUSIC_MIN_DURATION_MS = 45_000L
        const val MUSIC_MAX_DURATION_MS = 900_000L
    }
}
