package com.my.music.data.source.wave

import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.Track
import com.my.music.data.repository.LastFmRepository
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.TasteRepository
import com.my.music.data.repository.WaveBatch
import com.my.music.data.repository.WaveRepository
import com.my.music.data.repository.WaveSelection
import com.my.music.data.repository.WaveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Собирает бесконечную волну из лайков, истории, похожих артистов Last.fm
 * и находок по жанрам. Соотношение источников — 40/30/20/10.
 */
@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val chartsRepository: SoundCloudChartsRepository,
    private val lastFmRepository: LastFmRepository,
    private val tasteRepository: TasteRepository,
) : WaveRepository {

    private val _activeSession = MutableStateFlow<WaveSession?>(null)
    override val activeSession: StateFlow<WaveSession?> = _activeSession.asStateFlow()

    private val sessionMutex = Mutex()

    override suspend fun startSession(seeds: List<String>): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                sessionMutex.withLock {
                    val liked = likedTracksRepository.getLikedTracks().first()
                    val history = playHistoryRepository.getPlayHistory().first()

                    val session = WaveSession(
                        sessionId = UUID.randomUUID().toString(),
                        seedTrackIds = seeds.distinct(),
                        seedArtists = buildSeedArtists(liked, history),
                        seedGenres = buildSeedGenres(liked, history),
                        shownTrackIds = seeds.toSet(),
                        isRussianPriority = tasteRepository.isRussianPriority(),
                    )
                    _activeSession.value = session
                    session
                }
            }.rethrowCancellation()
        }

    override suspend fun nextBatch(size: Int): Result<WaveBatch> =
        withContext(Dispatchers.IO) {
            runCatching {
                sessionMutex.withLock {
                    buildNextBatch(size)
                }
            }.rethrowCancellation()
        }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        // Сразу помечаем сессию недействительной, чтобы летящий nextBatch
        // не успел дописать старую пачку в новую очередь.
        _activeSession.value = null
        sessionMutex.withLock {
            _activeSession.value = null
        }
    }

    override suspend fun generateWave(): Result<WaveSelection> = runCatching {
        val session = startSession().getOrThrow()
        val batch = nextBatch().getOrThrow()
        WaveSelection(
            tracks = batch.tracks,
            fromLikes = batch.session.fromLikes,
            fromHistory = batch.session.fromHistory,
            fromGenres = batch.session.fromGenres,
            fromSimilar = batch.session.fromSimilar,
        )
    }.rethrowCancellation()

    private suspend fun buildNextBatch(size: Int): WaveBatch {
        val session = _activeSession.value
            ?: error("Сессия волны не запущена")
        if (session.isTerminated) {
            return WaveBatch(emptyList(), session)
        }

        val target = size.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
        val liked = likedTracksRepository.getLikedTracks().first()
        val history = playHistoryRepository.getPlayHistory().first()

        val seedArtists = mergeDistinct(
            session.seedArtists,
            buildSeedArtists(liked, history),
        ).take(SEED_ARTIST_LIMIT)
        val seedGenres = mergeDistinct(
            session.seedGenres,
            buildSeedGenres(liked, history),
        ).take(SEED_GENRE_LIMIT)

        val knownIds = session.shownTrackIds.toMutableSet()
        val entries = LinkedHashMap<String, WavePick>()

        fun take(candidates: List<Track>, limit: Int, origin: WaveOrigin) {
            if (limit <= 0) return
            var taken = 0
            for (candidate in prioritize(candidates, session.isRussianPriority)) {
                if (taken >= limit || entries.size >= target) break
                if (candidate.id.isBlank() || !knownIds.add(candidate.id)) continue
                entries[candidate.id] = WavePick(candidate, origin)
                taken++
            }
        }

        val similarCandidates = loadSimilarTracks(
            seedArtists = seedArtists,
            excludedIds = knownIds,
        )
        val genreCandidates = loadGenreTracks(
            seedGenres = seedGenres,
            excludedIds = knownIds,
        )

        val likesQuota = (target * LIKES_SHARE).roundToInt()
        val historyQuota = (target * HISTORY_SHARE).roundToInt()
        val genresQuota = (target * GENRES_SHARE).roundToInt()
        val similarQuota = target - likesQuota - historyQuota - genresQuota

        take(similarCandidates, similarQuota, WaveOrigin.SIMILAR)
        take(liked, likesQuota, WaveOrigin.LIKES)
        take(history, historyQuota, WaveOrigin.HISTORY)
        take(genreCandidates, genresQuota, WaveOrigin.GENRES)

        // Если какой-то источник пуст, добираем из оставшихся пулов
        take(similarCandidates, target - entries.size, WaveOrigin.SIMILAR)
        take(liked, target - entries.size, WaveOrigin.LIKES)
        take(history, target - entries.size, WaveOrigin.HISTORY)
        take(genreCandidates, target - entries.size, WaveOrigin.GENRES)

        if (entries.size < target) {
            val chartTracks = chartsRepository.getTop50().getOrNull().orEmpty()
            take(chartTracks, target - entries.size, WaveOrigin.CATALOG)
        }

        val orderedEntries = entries.entries
            .shuffled()
            .let { shuffled ->
                if (session.isRussianPriority) {
                    shuffled.sortedByDescending { (_, pick) -> pick.track.cyrillicScore() }
                } else {
                    shuffled
                }
            }

        val tracks = orderedEntries.map { it.value.track }
        val counts = entries.values.groupingBy { it.origin }.eachCount()
        val updatedSession = session.copy(
            seedArtists = seedArtists,
            seedGenres = seedGenres,
            shownTrackIds = session.shownTrackIds + entries.keys,
            batchNumber = session.batchNumber + 1,
            isRussianPriority = tasteRepository.isRussianPriority(),
            isTerminated = tracks.isEmpty(),
            fromLikes = session.fromLikes + counts.getOrDefault(WaveOrigin.LIKES, 0),
            fromHistory = session.fromHistory + counts.getOrDefault(WaveOrigin.HISTORY, 0),
            fromGenres = session.fromGenres +
                counts.getOrDefault(WaveOrigin.GENRES, 0) +
                counts.getOrDefault(WaveOrigin.CATALOG, 0),
            fromSimilar = session.fromSimilar + counts.getOrDefault(WaveOrigin.SIMILAR, 0),
        )

        // Пока шёл сетевой запрос, сессию могли сбросить или заменить
        if (_activeSession.value?.sessionId != session.sessionId) {
            return WaveBatch(emptyList(), session.copy(isTerminated = true))
        }

        _activeSession.value = updatedSession
        return WaveBatch(tracks, updatedSession)
    }

    private suspend fun buildSeedArtists(
        liked: List<Track>,
        history: List<Track>,
    ): List<String> {
        val artists = LinkedHashMap<String, String>()

        tasteRepository.getTopArtists(SEED_ARTIST_LIMIT).forEach { taste ->
            addDistinct(artists, taste.value)
        }
        (history + liked).forEach { track ->
            addDistinct(artists, track.artist)
        }

        return artists.values.take(SEED_ARTIST_LIMIT)
    }

    private suspend fun buildSeedGenres(
        liked: List<Track>,
        history: List<Track>,
    ): List<String> {
        val genres = LinkedHashMap<String, String>()

        tasteRepository.getTopGenres(SEED_GENRE_LIMIT).forEach { taste ->
            addDistinct(genres, taste.value)
        }
        (liked + history).forEach { track ->
            addDistinct(genres, track.genre)
        }

        return genres.values.take(SEED_GENRE_LIMIT)
    }

    private suspend fun loadSimilarTracks(
        seedArtists: List<String>,
        excludedIds: Set<String>,
    ): List<Track> {
        if (seedArtists.isEmpty()) return emptyList()

        val similarArtists = LinkedHashMap<String, String>()
        for (seed in seedArtists.take(SIMILAR_SEED_ARTISTS)) {
            val found = lastFmRepository
                .getSimilarArtists(seed, limit = SIMILAR_ARTISTS_PER_SEED)
                .getOrNull()
                .orEmpty()
            found.forEach { addDistinct(similarArtists, it) }
            if (similarArtists.size >= SIMILAR_ARTISTS_TO_SEARCH) break
        }

        val picks = mutableListOf<Track>()
        for (artist in similarArtists.values.take(SIMILAR_ARTISTS_TO_SEARCH)) {
            val result = soundCloudRepository
                .search(query = artist, limit = SIMILAR_SEARCH_LIMIT)
                .getOrNull()
                .orEmpty()
            result.asSequence()
                .filter { it.id.isNotBlank() && it.id !in excludedIds }
                .take(SIMILAR_TRACKS_PER_ARTIST)
                .forEach { picks += it }
            if (picks.size >= SIMILAR_BUFFER_LIMIT) break
        }

        return picks.distinctBy { it.id }
    }

    private suspend fun loadGenreTracks(
        seedGenres: List<String>,
        excludedIds: Set<String>,
    ): List<Track> {
        if (seedGenres.isEmpty()) return emptyList()

        val picks = mutableListOf<Track>()
        for (genre in seedGenres.take(GENRE_SEEDS_TO_SEARCH)) {
            val result = soundCloudRepository
                .search(query = genre, limit = GENRE_SEARCH_LIMIT)
                .getOrNull()
                .orEmpty()
            result.asSequence()
                .filter { it.id.isNotBlank() && it.id !in excludedIds }
                .take(GENRE_TRACKS_PER_SEED)
                .forEach { picks += it }
            if (picks.size >= GENRE_BUFFER_LIMIT) break
        }

        return picks.distinctBy { it.id }
    }

    private fun prioritize(candidates: List<Track>, russianPriority: Boolean): List<Track> {
        val shuffled = candidates.shuffled()
        return if (russianPriority) {
            shuffled.sortedByDescending { it.cyrillicScore() }
        } else {
            shuffled
        }
    }

    private fun addDistinct(target: LinkedHashMap<String, String>, value: String?) {
        val display = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        target.putIfAbsent(display.lowercase(Locale.ROOT), display)
    }

    private fun mergeDistinct(first: List<String>, second: List<String>): List<String> {
        val result = LinkedHashMap<String, String>()
        first.forEach { addDistinct(result, it) }
        second.forEach { addDistinct(result, it) }
        return result.values.toList()
    }

    private fun Track.cyrillicScore(): Int {
        val text = "$title $artist"
        return if (text.any { it in CYRILLIC_RANGE }) 1 else 0
    }

    private data class WavePick(
        val track: Track,
        val origin: WaveOrigin,
    )

    private enum class WaveOrigin { SIMILAR, LIKES, HISTORY, GENRES, CATALOG }

    private companion object {
        const val MIN_BATCH_SIZE = 5
        const val MAX_BATCH_SIZE = 30

        const val LIKES_SHARE = 0.3f
        const val HISTORY_SHARE = 0.2f
        const val GENRES_SHARE = 0.1f

        const val SEED_ARTIST_LIMIT = 4
        const val SEED_GENRE_LIMIT = 5

        const val SIMILAR_SEED_ARTISTS = 2
        const val SIMILAR_ARTISTS_PER_SEED = 4
        const val SIMILAR_ARTISTS_TO_SEARCH = 5
        const val SIMILAR_SEARCH_LIMIT = 5
        const val SIMILAR_TRACKS_PER_ARTIST = 2
        const val SIMILAR_BUFFER_LIMIT = 12

        const val GENRE_SEEDS_TO_SEARCH = 2
        const val GENRE_SEARCH_LIMIT = 6
        const val GENRE_TRACKS_PER_SEED = 3
        const val GENRE_BUFFER_LIMIT = 6

        val CYRILLIC_RANGE = '\u0400'..'\u04FF'
    }
}
