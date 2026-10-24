package com.my.music.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.audio.cosineSimilarity
import com.my.music.core.util.SeasonProvider
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Track
import com.my.music.data.repository.EmbeddingRepository
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.RecentlyShownRepository
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
    private val recentlyShownRepository: RecentlyShownRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val recommendationPlanner: RecommendationPlanner,
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
                    updateRecommendations()
                }
        }
    }

    /**
     * Персональные рекомендации: артисты из лайков и истории.
     * Повторная загрузка — только по Pull-to-refresh, чтобы лента не менялась
     * после каждого прослушивания.
     */
    private fun updateRecommendations(forceRefresh: Boolean = false) {
        if (!forceRefresh && recommendationsLoaded) return

        recommendationsLoaded = true
        loadRecommendations(forceRefresh = forceRefresh)

        // Пока это только диагностика: сама выдача рекомендаций по-прежнему
        // строится на артистах и жанрах. Лог покажет, что YAMNet-векторы
        // реально считаются и сохраняются в Room.
        logEmbeddingSimilaritySample()
    }

    private fun loadRecommendations(forceRefresh: Boolean) {
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
                val recentlyShownIds = loadRecentlyShownIds()
                val queries = recommendationPlanner.buildQueries(
                    liked = _uiState.value.likedTracks,
                    history = _uiState.value.recentTracks,
                )
                val result = loadRecommendationTracks(queries, recentlyShownIds)
                if (requestId != recommendationsRequestId) return@launch

                result.fold(
                    onSuccess = { tracks ->
                        _uiState.update {
                            it.copy(
                                recommendations = tracks,
                                recommendationsError = null,
                            )
                        }
                        markRecentlyShown(tracks)
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
        queries: List<RecommendationQuery>,
        recentlyShownIds: Set<String>,
    ): Result<List<Track>> = coroutineScope {
        val primaryResults = queries
            .map { query ->
                async {
                    query to youtubeRepository.search(query.text, RECOMMENDATION_SEARCH_LIMIT)
                }
            }
            .awaitAll()

        val primaryCandidates = primaryResults
            .flatMap { (query, result) ->
                result.getOrElse { emptyList() }
                    .asSequence()
                    .filter { track ->
                        val allowedArtists = query.allowedArtists
                        val matchesArtist = allowedArtists == null ||
                            recommendationPlanner.matchesSeedArtist(track, allowedArtists)
                        track.isEligible(recentlyShownIds) && matchesArtist
                    }
                    .toList()
            }
            .distinctBy { it.id }

        val hasPersonalQuery = queries.any { it.allowedArtists != null }
        val primarySelection = recommendationPlanner.selectTracks(primaryCandidates)
        val primaryHasSuccess = primaryResults.any { it.second.isSuccess }
        val primaryError = primaryResults.firstNotNullOfOrNull { it.second.exceptionOrNull() }

        if (!hasPersonalQuery) {
            return@coroutineScope if (primaryHasSuccess) {
                Result.success(primarySelection)
            } else {
                Result.failure(
                    primaryError ?: IllegalStateException("Не удалось загрузить рекомендации"),
                )
            }
        }

        if (primarySelection.size >= MIN_RECOMMENDATION_TRACKS) {
            return@coroutineScope Result.success(primarySelection)
        }

        val fallbackResult = youtubeRepository.search(
            POPULAR_QUERY,
            RECOMMENDATION_SEARCH_LIMIT,
        )
        val fallbackCandidates = fallbackResult
            .getOrElse { emptyList() }
            .asSequence()
            .filter { it.isEligible(recentlyShownIds) }
            .distinctBy { it.id }
            .toList()
        val merged = recommendationPlanner.appendTracks(
            selected = primarySelection,
            candidates = fallbackCandidates,
        )

        if (merged.isNotEmpty()) {
            return@coroutineScope Result.success(merged)
        }

        if (primaryHasSuccess || fallbackResult.isSuccess) {
            Result.success(emptyList())
        } else {
            Result.failure(
                primaryError
                    ?: fallbackResult.exceptionOrNull()
                    ?: IllegalStateException("Не удалось загрузить рекомендации"),
            )
        }
    }

    private fun Track.isEligible(recentlyShownIds: Set<String>): Boolean =
        id !in recentlyShownIds &&
            durationMs in MUSIC_MIN_DURATION_MS..MUSIC_MAX_DURATION_MS &&
            isStudioOriginal()

    /**
     * Отсекаем переводы, каверы, ремиксы, live-версии и прочие нестудийные записи.
     */
    private fun Track.isStudioOriginal(): Boolean {
        if (title.isBlank() || title.none { it.isLetter() }) return false

        val text = "$title $artist"
        if (NON_STUDIO_PHRASES.any { text.contains(it, ignoreCase = true) }) return false
        return !NON_STUDIO_WORDS.containsMatchIn(text)
    }

    /**
     * Диагностический сэмпл: два случайных трека из истории/лайков, для которых
     * уже есть эмбеддинг. Ничего не меняет в UI и не влияет на рекомендации.
     */
    private fun logEmbeddingSimilaritySample() {
        viewModelScope.launch {
            val candidates = (_uiState.value.recentTracks + _uiState.value.likedTracks)
                .distinctBy { it.id }
            if (candidates.size < 2) return@launch

            val embeddings = runCatching {
                embeddingRepository.getEmbeddings(candidates.map { it.id })
            }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(tag, "Не удалось прочитать аудио-эмбеддинги: ${error.message}", error)
                }
                .getOrDefault(emptyMap())

            val withEmbeddings = candidates.mapNotNull { track ->
                embeddings[track.id]?.let { vector -> track to vector }
            }
            if (withEmbeddings.size < 2) return@launch

            val pair = withEmbeddings.shuffled().take(2)
            val similarity = cosineSimilarity(pair[0].second, pair[1].second)
            Log.d(
                tag,
                "Косинусное сходство эмбеддингов: " +
                    "'${pair[0].first.title}' (${pair[0].first.id}) и " +
                    "'${pair[1].first.title}' (${pair[1].first.id}) = $similarity",
            )
        }
    }

    private suspend fun loadRecentlyShownIds(): Set<String> =
        runCatching {
            recentlyShownRepository.getRecentlyShownIds(
                now = System.currentTimeMillis(),
                ttlMs = RECENTLY_SHOWN_TTL_MS,
            )
        }
            .rethrowCancellation()
            .onFailure { error ->
                Log.e(tag, "Не удалось прочитать recently_shown: ${error.message}", error)
            }
            .getOrDefault(emptySet())

    private suspend fun markRecentlyShown(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        runCatching {
            recentlyShownRepository.markShown(
                trackIds = tracks.map { it.id },
                now = System.currentTimeMillis(),
                ttlMs = RECENTLY_SHOWN_TTL_MS,
            )
        }
            .rethrowCancellation()
            .onFailure { error ->
                Log.e(tag, "Не удалось сохранить recently_shown: ${error.message}", error)
            }
    }

    /** Принудительное обновление для Pull-to-refresh. */
    fun refresh() {
        updateRecommendations(forceRefresh = true)
    }

    private companion object {
        const val RECOMMENDATION_SEARCH_LIMIT = 24
        const val MIN_RECOMMENDATION_TRACKS = 10
        const val RECENTLY_SHOWN_TTL_MS = 24 * 60 * 60 * 1000L
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
