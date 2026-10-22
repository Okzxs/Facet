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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreSection(
    val genre: String,
    val tracks: List<Track>,
)

data class HomeUiState(
    val isRefreshing: Boolean = false,
    val isRecommendationsLoading: Boolean = true,
    val recommendationsError: String? = null,
    val recommendations: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val memories: List<SeasonMemory> = emptyList(),
    val memorySeason: String = SeasonProvider.currentSeason(),
    val genreSections: List<GenreSection> = emptyList(),
)

private data class LocalSignals(
    val recent: List<Track>,
    val liked: List<Track>,
    val genres: List<GenreSection>,
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

    private var recommendationsJob: Job? = null
    private var recommendationsKey: String? = null
    private var recommendationsRequestId = 0

    init {
        observeLocalData()
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

                LocalSignals(recent = recent, liked = liked, genres = genres)
            }
                .distinctUntilChanged()
                .collect { signals ->
                    _uiState.update {
                        it.copy(
                            recentTracks = signals.recent,
                            likedTracks = signals.liked,
                            genreSections = signals.genres,
                        )
                    }
                    updateRecommendations(signals.liked, signals.recent)
                }
        }
    }

    /**
     * Персональные рекомендации: жанр и артист из последних лайков и истории.
     * Если данных нет — загружаем популярные треки.
     */
    private fun updateRecommendations(
        liked: List<Track>,
        recent: List<Track>,
        forceRefresh: Boolean = false,
    ) {
        val excludedIds = (liked + recent).mapTo(mutableSetOf()) { it.id }
        val current = _uiState.value.recommendations
        val remaining = current.filterNot { it.id in excludedIds }
        val lostAllRecommendations = current.isNotEmpty() && remaining.isEmpty()

        if (remaining.size != current.size) {
            _uiState.update { it.copy(recommendations = remaining) }
        }

        val queries = buildRecommendationQueries(liked, recent)
        val key = queries.joinToString(separator = "|")
        if (!forceRefresh && key == recommendationsKey && !lostAllRecommendations) return

        recommendationsKey = key
        loadRecommendations(
            queries = queries,
            excludedIds = excludedIds,
            forceRefresh = forceRefresh,
        )
    }

    private fun buildRecommendationQueries(
        liked: List<Track>,
        recent: List<Track>,
    ): List<String> {
        val signals = (liked.take(PERSONALIZATION_WINDOW) + recent.take(PERSONALIZATION_WINDOW))
            .distinctBy { it.id }
        val genre = signals.firstNotNullOfOrNull { track ->
            track.genre?.trim()?.takeIf { it.isNotEmpty() }
        }
        val artist = signals.firstNotNullOfOrNull { track ->
            track.artist.trim().takeIf { it.isNotEmpty() }
        }

        return buildList {
            genre?.let { add("$it music mix") }
            artist?.let { add("$it best songs") }
        }.distinct().ifEmpty { listOf(POPULAR_QUERY) }
    }

    private fun loadRecommendations(
        queries: List<String>,
        excludedIds: Set<String>,
        forceRefresh: Boolean,
    ) {
        val requestId = ++recommendationsRequestId
        recommendationsJob?.cancel()
        recommendationsJob = viewModelScope.launch {
            val hasTracks = _uiState.value.recommendations.isNotEmpty()
            _uiState.update {
                it.copy(
                    isRefreshing = forceRefresh,
                    isRecommendationsLoading = !hasTracks,
                    recommendationsError = if (!hasTracks || forceRefresh) {
                        null
                    } else {
                        it.recommendationsError
                    },
                )
            }

            try {
                val result = loadRecommendationTracks(queries, excludedIds)
                if (requestId != recommendationsRequestId) return@launch

                result.fold(
                    onSuccess = { tracks ->
                        _uiState.update {
                            it.copy(
                                recommendations = tracks,
                                recommendationsError = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(tag, "Ошибка загрузки рекомендаций: ${error.message}", error)
                        _uiState.update {
                            it.copy(
                                recommendationsError = error.message
                                    ?: "Не удалось загрузить рекомендации",
                            )
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == recommendationsRequestId) {
                    Log.e(tag, "Ошибка загрузки рекомендаций: ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            recommendationsError = e.message
                                ?: "Не удалось загрузить рекомендации",
                        )
                    }
                }
            } finally {
                if (requestId == recommendationsRequestId) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isRecommendationsLoading = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadRecommendationTracks(
        queries: List<String>,
        excludedIds: Set<String>,
    ): Result<List<Track>> = coroutineScope {
        val primaryResults = queries
            .map { query ->
                async { youtubeRepository.search(query, RECOMMENDATION_SEARCH_LIMIT) }
            }
            .awaitAll()

        val primaryTracks = primaryResults
            .flatMap { it.getOrElse { emptyList() } }
            .asRecommendations(excludedIds)

        if (primaryTracks.isNotEmpty()) {
            return@coroutineScope Result.success(primaryTracks)
        }

        val hasPersonalQuery = queries.any { it != POPULAR_QUERY }
        if (!hasPersonalQuery) {
            val firstError = primaryResults.firstNotNullOfOrNull { it.exceptionOrNull() }
            return@coroutineScope if (primaryResults.any { it.isSuccess }) {
                Result.success(emptyList())
            } else {
                Result.failure(
                    firstError ?: IllegalStateException("Не удалось загрузить рекомендации"),
                )
            }
        }

        val fallbackResult = youtubeRepository.search(
            POPULAR_QUERY,
            RECOMMENDATION_SEARCH_LIMIT,
        )
        fallbackResult.fold(
            onSuccess = { Result.success(it.asRecommendations(excludedIds)) },
            onFailure = { fallbackError ->
                val primaryError = primaryResults.firstNotNullOfOrNull { it.exceptionOrNull() }
                Result.failure(primaryError ?: fallbackError)
            },
        )
    }

    private fun List<Track>.asRecommendations(excludedIds: Set<String>): List<Track> =
        asSequence()
            .filter { it.durationMs in MUSIC_MIN_DURATION_MS..MUSIC_MAX_DURATION_MS }
            .filterNot { it.id in excludedIds }
            .distinctBy { it.id }
            .take(RECOMMENDATION_TRACKS_LIMIT)
            .toList()

    /** Принудительное обновление для Pull-to-refresh. */
    fun refresh() {
        val state = _uiState.value
        updateRecommendations(
            liked = state.likedTracks,
            recent = state.recentTracks,
            forceRefresh = true,
        )
    }

    private companion object {
        const val POPULAR_QUERY = "popular music 2026"
        const val RECOMMENDATION_SEARCH_LIMIT = 12
        const val RECOMMENDATION_TRACKS_LIMIT = 10
        const val PERSONALIZATION_WINDOW = 20
        const val MUSIC_MIN_DURATION_MS = 45_000L
        const val MUSIC_MAX_DURATION_MS = 900_000L
    }
}
