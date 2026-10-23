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
    private var recommendationsLoaded = false
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
     * Повторная загрузка — только по Pull-to-refresh, чтобы лента не менялась
     * после каждого прослушивания.
     */
    private fun updateRecommendations(
        liked: List<Track>,
        recent: List<Track>,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && recommendationsLoaded) return

        recommendationsLoaded = true
        loadRecommendations(
            queries = buildRecommendationQueries(liked, recent),
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
            genre?.let { add("$it official audio") }
            artist?.let { add("$it official audio") }
        }.distinct().ifEmpty { listOf(POPULAR_QUERY) }
    }

    private fun loadRecommendations(
        queries: List<String>,
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
                val result = loadRecommendationTracks(queries)
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
    ): Result<List<Track>> = coroutineScope {
        val primaryResults = queries
            .map { query ->
                async { youtubeRepository.search(query, RECOMMENDATION_SEARCH_LIMIT) }
            }
            .awaitAll()

        val primaryTracks = primaryResults
            .flatMap { it.getOrElse { emptyList() } }
            .asRecommendations()

        val hasPersonalQuery = queries.any { it != POPULAR_QUERY }
        if (!hasPersonalQuery) {
            val firstError = primaryResults.firstNotNullOfOrNull { it.exceptionOrNull() }
            return@coroutineScope if (primaryResults.any { it.isSuccess }) {
                Result.success(primaryTracks)
            } else {
                Result.failure(
                    firstError ?: IllegalStateException("Не удалось загрузить рекомендации"),
                )
            }
        }

        if (primaryTracks.size >= MIN_RECOMMENDATION_TRACKS) {
            return@coroutineScope Result.success(primaryTracks)
        }

        val fallbackResult = youtubeRepository.search(
            POPULAR_QUERY,
            RECOMMENDATION_SEARCH_LIMIT,
        )
        val fallbackTracks = fallbackResult.getOrElse { emptyList() }.asRecommendations()
        val merged = (primaryTracks + fallbackTracks)
            .distinctBy { it.id }
            .take(RECOMMENDATION_TRACKS_LIMIT)

        if (merged.isNotEmpty()) {
            return@coroutineScope Result.success(merged)
        }

        val primaryError = primaryResults.firstNotNullOfOrNull { it.exceptionOrNull() }
        if (primaryResults.any { it.isSuccess } || fallbackResult.isSuccess) {
            Result.success(emptyList())
        } else {
            Result.failure(
                primaryError
                    ?: fallbackResult.exceptionOrNull()
                    ?: IllegalStateException("Не удалось загрузить рекомендации"),
            )
        }
    }

    private fun List<Track>.asRecommendations(): List<Track> =
        asSequence()
            .filter { it.durationMs in MUSIC_MIN_DURATION_MS..MUSIC_MAX_DURATION_MS }
            .filter { it.isStudioOriginal() }
            .distinctBy { it.id }
            .take(RECOMMENDATION_TRACKS_LIMIT)
            .toList()

    /**
     * Отсекаем переводы, каверы, ремиксы, live-версии и прочие нестудийные записи.
     */
    private fun Track.isStudioOriginal(): Boolean {
        if (title.isBlank() || title.none { it.isLetter() }) return false

        val text = "$title $artist"
        if (NON_STUDIO_PHRASES.any { text.contains(it, ignoreCase = true) }) return false
        return !NON_STUDIO_WORDS.containsMatchIn(text)
    }

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
        const val POPULAR_QUERY = "popular songs official audio"
        const val RECOMMENDATION_SEARCH_LIMIT = 24
        const val RECOMMENDATION_TRACKS_LIMIT = 15
        const val MIN_RECOMMENDATION_TRACKS = 10
        const val PERSONALIZATION_WINDOW = 20
        const val MUSIC_MIN_DURATION_MS = 45_000L
        const val MUSIC_MAX_DURATION_MS = 900_000L

        val NON_STUDIO_PHRASES = listOf(
            "перевод",
            "перевед",
            "на русском",
            "русская версия",
            "русский перевод",
            "на английском",
            "english version",
            "russian version",
            "на разных языках",
            "разных языках",
            "многоязыч",
            "мультиязыч",
            "all languages",
            "multiple languages",
            "different languages",
            "many languages",
            "кавер",
            "ремикс",
            "мэшап",
            "мешап",
            "караоке",
            "минус",
            "инструментал",
            "акустик",
            "концерт",
            "выступление",
            "оркестр",
            "пианино",
            "пародия",
            "реакция",
            "нейросет",
            "ии кавер",
            "колыбельная",
            "трибьют",
            "разбор",
            "аккорды",
            "урок",
            "fan made",
            "fanmade",
        )

        val NON_STUDIO_WORDS = Regex(
            pattern = "\\b(" +
                "cover|remix|remake|rework|bootleg|mashup|megamix|mix|" +
                "karaoke|instrumental|live|concert|performance|acoustic|piano|" +
                "synthesia|orchestra|parody|reaction|tutorial|chords|slowed|" +
                "reverb|nightcore|8d|lullaby|tribute|translation|translated|" +
                "multilingual|sped up|speed up|bass boosted|a cappella|acapella" +
                ")\\b",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
