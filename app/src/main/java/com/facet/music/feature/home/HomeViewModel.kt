package com.facet.music.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.util.SeasonProvider
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Source
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.RecentlyShownRepository
import com.facet.music.data.repository.SoundCloudChartsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.data.source.external.DeezerProvider
import com.facet.music.data.source.external.DeezerRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** Чарты SoundCloud для блока «Чарты». */
    val charts: List<Track> = emptyList(),
    /** Новинки лейблов Deezer для блока «Недавние релизы». */
    val newReleases: List<DeezerRelease> = emptyList(),
    /** Идёт загрузка чартов и новинок — показываем скелетоны. */
    val isExtrasLoading: Boolean = true,
)

private data class LocalSignals(
    val recent: List<Track>,
    val liked: List<Track>,
    val genres: List<GenreSection>,
)

private data class CandidateLoadResult(
    val candidates: List<Track>,
    val anySuccess: Boolean,
    val firstError: Throwable?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val chartsRepository: SoundCloudChartsRepository,
    private val deezerProvider: DeezerProvider,
    private val downloadRepository: DownloadRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val recentlyShownRepository: RecentlyShownRepository,
    private val dislikedTrackDao: DislikedTrackDao,
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
        loadHomeExtras()
    }

    // ─── Блоки главной: чарты и новинки ──────────────────────────────────

    /** Событие «открыть плеер»: навигация — забота экрана, не ViewModel. */
    private val _openPlayer = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Track, List<Track>>>(
        extraBufferCapacity = 1,
    )
    val openPlayer: SharedFlow<Pair<Track, List<Track>>> = _openPlayer.asSharedFlow()

    /**
     * Чарты SoundCloud и новинки Deezer. Ошибки не рушат главную: блок просто
     * не показывается, если данных нет.
     */
    fun loadHomeExtras(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isExtrasLoading = it.charts.isEmpty() && it.newReleases.isEmpty())
            }
            val charts = runCatching { chartsRepository.getTop50(forceRefresh) }
                .getOrNull()
                ?.getOrNull()
                .orEmpty()
                .take(HOME_CHARTS_LIMIT)
            val releases = runCatching { deezerProvider.fetchNewReleases(HOME_RELEASES_LIMIT) }
                .getOrNull()
                ?.getOrNull()
                .orEmpty()
            _uiState.update {
                it.copy(
                    charts = charts.ifEmpty { it.charts },
                    newReleases = releases.ifEmpty { it.newReleases },
                    isExtrasLoading = false,
                )
            }
        }
    }

    /** Новинка — это альбом: треки грузим при нажатии и отдаём в плеер. */
    fun playRelease(release: DeezerRelease) {
        viewModelScope.launch {
            val tracks = deezerProvider.fetchAlbumTracks(release.id).getOrNull().orEmpty()
            if (tracks.isNotEmpty()) _openPlayer.emit(tracks.first() to tracks)
        }
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
                // Снимок персональных данных на момент запуска: лента не должна
                // меняться, пока пользователь слушает или лайкает треки.
                val liked = _uiState.value.likedTracks
                val history = _uiState.value.recentTracks
                val recentlyShownIds = loadRecentlyShownIds()
                val dislikedTrackIds = loadDislikedTrackIds()
                // Лента не должна «открывать» то, что уже лайкнуто, слушалось
                // или лежит офлайн: знакомые треки не занимают слоты новизны.
                val excludedTrackIds = buildSet {
                    liked.forEach { add(it.id) }
                    history.forEach { add(it.id) }
                    addAll(loadDownloadedIds())
                }
                val result = loadRecommendationTracks(
                    liked = liked,
                    history = history,
                    recentlyShownIds = recentlyShownIds,
                    dislikedTrackIds = dislikedTrackIds,
                    excludedTrackIds = excludedTrackIds,
                )
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
        liked: List<Track>,
        history: List<Track>,
        recentlyShownIds: Set<String>,
        dislikedTrackIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): Result<List<Track>> = coroutineScope {
        val hasPersonalData = liked.isNotEmpty() || history.isNotEmpty()
        val trackSeeds = recommendationPlanner.selectTrackSeeds(
            recommendationPlanner.buildTrackSeeds(liked, history),
        )
        val related = loadRelatedCandidates(trackSeeds, recentlyShownIds, excludedTrackIds)
        val relatedCandidates = recommendationPlanner.filterDisliked(
            candidates = related.candidates,
            dislikedTrackIds = dislikedTrackIds,
        )
        val dominantArtists = recommendationPlanner.findDominantArtists(liked, history)

        val relatedSelection = recommendationPlanner.selectTracks(
            candidates = recommendationPlanner.dedupeByTitle(relatedCandidates),
            maxPerArtist = MAX_TRACKS_PER_ARTIST,
            dominantArtists = dominantArtists,
            maxPerDominantArtist = MAX_TRACKS_PER_DOMINANT_ARTIST,
        )

        if (relatedSelection.size >= MIN_RECOMMENDATION_TRACKS) {
            return@coroutineScope Result.success(relatedSelection)
        }

        // Холодный старт: лайков и истории нет, related-сидов тоже.
        // Чарты SoundCloud — единственный источник без персонализации.
        if (!hasPersonalData) {
            return@coroutineScope loadChartRecommendations(
                recentlyShownIds = recentlyShownIds,
                dislikedTrackIds = dislikedTrackIds,
                excludedTrackIds = excludedTrackIds,
            )
        }

        val fallbackQueries = recommendationPlanner.buildArtistFallbackQueries(
            liked = liked,
            history = history,
        )
        val fallback = loadFallbackCandidates(fallbackQueries, recentlyShownIds, excludedTrackIds)
        val fallbackCandidates = recommendationPlanner.filterDisliked(
            candidates = fallback.candidates,
            dislikedTrackIds = dislikedTrackIds,
        )
        val selected = recommendationPlanner.appendTracks(
            selected = relatedSelection,
            candidates = recommendationPlanner.dedupeByTitle(fallbackCandidates),
            maxPerArtist = MAX_TRACKS_PER_ARTIST,
            dominantArtists = dominantArtists,
            maxPerDominantArtist = MAX_TRACKS_PER_DOMINANT_ARTIST,
        )

        when {
            selected.isNotEmpty() -> Result.success(selected)
            related.anySuccess || fallback.anySuccess -> Result.success(emptyList())
            else -> Result.failure(
                related.firstError
                    ?: fallback.firstError
                    ?: IllegalStateException("Не удалось загрузить рекомендации"),
            )
        }
    }

    /**
     * Запрашивает related-выдачу для 4–6 сид-треков. Ошибка одного источника
     * не ломает всю ленту: остальные сиды и fallback продолжают работать.
     */
    private suspend fun loadRelatedCandidates(
        seeds: List<TrackSeed>,
        recentlyShownIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): CandidateLoadResult = coroutineScope {
        if (seeds.isEmpty()) {
            return@coroutineScope CandidateLoadResult(
                candidates = emptyList(),
                anySuccess = false,
                firstError = null,
            )
        }

        val seedIds = seeds.mapTo(HashSet()) { it.track.id }
        val results = seeds
            .map { seed ->
                async {
                    val result: Result<List<Track>> = when (seed.track.source) {
                        Source.YOUTUBE -> youtubeRepository.getRelated(
                            trackUrl = seed.url,
                            limit = RELATED_ITEMS_LIMIT,
                        )

                        Source.SOUNDCLOUD -> soundCloudRepository.getRelated(
                            trackUrl = seed.url,
                            limit = RELATED_ITEMS_LIMIT,
                        )

                        else -> Result.success(emptyList())
                    }
                    result
                }
            }
            .awaitAll()

        val candidates = results
            .flatMap { result -> result.getOrElse { emptyList() } }
            .asSequence()
            // Не рекомендуем сам сид и то, что уже показывали за последние 24 часа.
            // Артиста здесь не проверяем: related-выдача специально приводит новых.
            .filter { track -> track.id !in seedIds && track.isEligible(recentlyShownIds, excludedTrackIds) }
            .distinctBy { it.id }
            .toList()

        val failures = results.count { it.isFailure }
        Log.d(
            tag,
            "Related: сидов=${seeds.size}, кандидатов=${candidates.size}, ошибок=$failures",
        )
        results.forEach { result ->
            result.exceptionOrNull()?.let { error ->
                Log.w(tag, "Related-запрос не удался: ${error.message}", error)
            }
        }

        CandidateLoadResult(
            candidates = candidates,
            anySuccess = results.any { it.isSuccess },
            firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() },
        )
    }

    /**
     * Точечный fallback по топ-артистам. Принимаем только треки, у которых
     * поле `artist` строго совпало с сид-артистом.
     */
    private suspend fun loadFallbackCandidates(
        queries: List<RecommendationQuery>,
        recentlyShownIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): CandidateLoadResult = coroutineScope {
        if (queries.isEmpty()) {
            return@coroutineScope CandidateLoadResult(
                candidates = emptyList(),
                anySuccess = false,
                firstError = null,
            )
        }

        val results = queries
            .map { query ->
                async {
                    query to youtubeRepository.search(
                        query = query.text,
                        limit = RECOMMENDATION_SEARCH_LIMIT,
                    )
                }
            }
            .awaitAll()

        val candidates = results
            .flatMap { (query, result) ->
                result.getOrElse { emptyList() }
                    .asSequence()
                    .filter { track ->
                        recommendationPlanner.matchesExactArtist(
                            track = track,
                            allowedArtists = query.allowedArtists,
                        ) && track.isEligible(recentlyShownIds, excludedTrackIds)
                    }
                    .toList()
            }
            .distinctBy { it.id }

        Log.d(
            tag,
            "Fallback: запросов=${queries.size}, кандидатов=${candidates.size}",
        )

        CandidateLoadResult(
            candidates = candidates,
            anySuccess = results.any { it.second.isSuccess },
            firstError = results.firstNotNullOfOrNull { it.second.exceptionOrNull() },
        )
    }

    /**
     * Холодный старт: лайков и истории нет. Вместо глобального запроса
     * «popular songs» берём чарт SoundCloud — он хотя бы музыкальный,
     * а не случайная выдача YouTube. Если чарт недоступен — последний
     * фолбэк на YouTube-поиск.
     */
    private suspend fun loadChartRecommendations(
        recentlyShownIds: Set<String>,
        dislikedTrackIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): Result<List<Track>> {
        val chart = chartsRepository.getTop50().getOrNull().orEmpty()
            .asSequence()
            .filter { it.isEligible(recentlyShownIds, excludedTrackIds) }
            .toList()

        val candidates = recommendationPlanner.filterDisliked(
            candidates = recommendationPlanner.dedupeByTitle(chart),
            dislikedTrackIds = dislikedTrackIds,
        )

        val selected = recommendationPlanner.selectTracks(
            candidates = candidates,
            maxPerArtist = MAX_TRACKS_PER_ARTIST,
        )
        if (selected.isNotEmpty()) return Result.success(selected)

        return loadPopularRecommendations(
            recentlyShownIds = recentlyShownIds,
            dislikedTrackIds = dislikedTrackIds,
            excludedTrackIds = excludedTrackIds,
        )
    }

    /**
     * Последний фолбэк холодного старта — YouTube-поиск.
     * Глобальный запрос разрешён только когда нет ни лайков, ни истории.
     */
    private suspend fun loadPopularRecommendations(
        recentlyShownIds: Set<String>,
        dislikedTrackIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): Result<List<Track>> {
        val popularResult = youtubeRepository.search(
            query = POPULAR_QUERY,
            limit = RECOMMENDATION_SEARCH_LIMIT,
        )
        if (popularResult.isFailure) {
            return Result.failure(
                popularResult.exceptionOrNull()
                    ?: IllegalStateException("Не удалось загрузить рекомендации"),
            )
        }

        val candidates = recommendationPlanner.filterDisliked(
            candidates = recommendationPlanner.dedupeByTitle(
                popularResult
                    .getOrDefault(emptyList())
                    .asSequence()
                    .filter { it.isEligible(recentlyShownIds, excludedTrackIds) }
                    .distinctBy { it.id }
                    .toList(),
            ),
            dislikedTrackIds = dislikedTrackIds,
        )
        val selected = recommendationPlanner.selectTracks(
            candidates = candidates,
            maxPerArtist = MAX_TRACKS_PER_ARTIST,
        )
        return Result.success(selected)
    }

    private fun Track.isEligible(
        recentlyShownIds: Set<String>,
        excludedTrackIds: Set<String>,
    ): Boolean = id !in recentlyShownIds &&
        id !in excludedTrackIds &&
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

    /** Дизлайки пользователя: их треки не должны попадать в рекомендации. */
    private suspend fun loadDislikedTrackIds(): Set<String> =
        runCatching {
            dislikedTrackDao.getAll().mapTo(HashSet()) { it.trackId }
        }.rethrowCancellation()
            .onFailure { error ->
                Log.w(tag, "Не удалось загрузить дизлайки: ${error.message}", error)
            }
            .getOrDefault(emptySet())

    /** Скачанные треки: не предлагаем то, что уже лежит офлайн. */
    private suspend fun loadDownloadedIds(): Set<String> =
        runCatching { downloadRepository.observeDownloadedIds().first() }
            .rethrowCancellation()
            .onFailure { error ->
                Log.w(tag, "Не удалось загрузить скачанные: ${error.message}", error)
            }
            .getOrDefault(emptySet())

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
        loadHomeExtras(forceRefresh = true)
    }

    private companion object {
        const val HOME_CHARTS_LIMIT = 10
        const val HOME_RELEASES_LIMIT = 10
        const val RECOMMENDATION_SEARCH_LIMIT = 24
        const val RELATED_ITEMS_LIMIT = 30
        const val MIN_RECOMMENDATION_TRACKS = 10
        const val MAX_TRACKS_PER_ARTIST = 2
        const val MAX_TRACKS_PER_DOMINANT_ARTIST = 3
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
