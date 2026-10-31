package com.facet.music.data.source.wave

import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LastFmRepository
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.SoundCloudChartsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveBatch
import com.facet.music.data.repository.WaveRepository
import com.facet.music.data.repository.WaveSelection
import com.facet.music.data.repository.WaveSession
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
 *
 * Чтобы подборка не превращалась в кашу, кандидаты каждого источника
 * делятся на два пула:
 * - строгий — кириллица (при RU-приоритете) и подтверждённое совпадение
 *   артиста/жанра; он заполняет квоту первым;
 * - запасной — латиница и неподтверждённые результаты; он подключается,
 *   только когда строгих треков не хватило на всю пачку.
 */
@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val chartsRepository: SoundCloudChartsRepository,
    private val lastFmRepository: LastFmRepository,
    private val tasteRepository: TasteRepository,
    private val dislikedTrackDao: DislikedTrackDao,
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

    override suspend fun startRadio(seed: Track): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                sessionMutex.withLock {
                    val similar = lastFmRepository
                        .getSimilarArtists(seed.artist, limit = RADIO_SIMILAR_ARTISTS)
                        .getOrNull()
                        .orEmpty()
                    val session = WaveSession(
                        sessionId = UUID.randomUUID().toString(),
                        seedTrackIds = listOf(seed.id),
                        seedArtists = (listOf(seed.artist) + similar)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(SEED_ARTIST_LIMIT),
                        seedGenres = listOfNotNull(
                            seed.genre?.takeIf { isUsefulGenre(it) },
                        ),
                        shownTrackIds = setOf(seed.id),
                        isRussianPriority = tasteRepository.isRussianPriority(),
                        radioMode = true,
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
        val russianPriority = tasteRepository.isRussianPriority()

        val liked = likedTracksRepository.getLikedTracks().first()
        val history = playHistoryRepository.getPlayHistory().first()

        // В режиме радио семена не разбавляются вкусами: иначе в очередь
        // от конкретного трека попадали бы случайные лайки и история.
        val seedArtists = if (session.radioMode) {
            session.seedArtists.take(SEED_ARTIST_LIMIT)
        } else {
            mergeDistinct(
                session.seedArtists,
                buildSeedArtists(liked, history),
            ).take(SEED_ARTIST_LIMIT)
        }
        val seedGenres = if (session.radioMode) {
            session.seedGenres.take(SEED_GENRE_LIMIT)
        } else {
            mergeDistinct(
                session.seedGenres,
                buildSeedGenres(liked, history),
            ).take(SEED_GENRE_LIMIT)
        }

        val knownIds = session.shownTrackIds.toMutableSet()
        val entries = LinkedHashMap<String, WavePick>()
        // Дизлайки и дубликаты одного трека из разных загрузок не должны
        // попадать в волну: первое — осознанный отказ, второе — визуальный шум.
        val dislikedIds = loadDislikedIds()
        val seenTitles = HashSet<String>()

        fun take(candidates: List<Track>, limit: Int, origin: WaveOrigin) {
            if (limit <= 0) return
            var taken = 0
            for (candidate in prioritize(candidates, russianPriority)) {
                if (taken >= limit || entries.size >= target) break
                if (candidate.id.isBlank()) continue
                if (candidate.id in dislikedIds) continue
                if (!seenTitles.add(candidate.titleKey())) continue
                if (!knownIds.add(candidate.id)) continue
                entries[candidate.id] = WavePick(candidate, origin)
                taken++
            }
        }

        val similar = loadSimilarTracks(
            seedArtists = seedArtists,
            excludedIds = session.shownTrackIds,
            russianPriority = russianPriority,
        )
        val genres = loadGenreTracks(
            seedGenres = seedGenres,
            excludedIds = session.shownTrackIds,
            russianPriority = russianPriority,
        )
        val likedPool = splitByLanguage(liked, russianPriority)
        val historyPool = splitByLanguage(history, russianPriority)

        val likesQuota = if (session.radioMode) 0 else (target * LIKES_SHARE).roundToInt()
        val historyQuota = if (session.radioMode) 0 else (target * HISTORY_SHARE).roundToInt()
        val genresQuota = if (session.radioMode) 0 else (target * GENRES_SHARE).roundToInt()
        val similarQuota = target - likesQuota - historyQuota - genresQuota

        // ── Фаза 1: строгие треки в пределах квот ────────────────────────
        take(similar.strict, similarQuota, WaveOrigin.SIMILAR)
        take(likedPool.strict, likesQuota, WaveOrigin.LIKES)
        take(historyPool.strict, historyQuota, WaveOrigin.HISTORY)
        take(genres.strict, genresQuota, WaveOrigin.GENRES)

        // ── Фаза 2: строгие сверх квот — если какой-то источник пуст ─────
        take(similar.strict, target - entries.size, WaveOrigin.SIMILAR)
        if (!session.radioMode) {
            take(likedPool.strict, target - entries.size, WaveOrigin.LIKES)
            take(historyPool.strict, target - entries.size, WaveOrigin.HISTORY)
            take(genres.strict, target - entries.size, WaveOrigin.GENRES)
        }

        // ── Фаза 3: запасные (другой язык / жанр не подтверждён) ─────────
        take(similar.loose, target - entries.size, WaveOrigin.SIMILAR)
        if (!session.radioMode) {
            take(likedPool.loose, target - entries.size, WaveOrigin.LIKES)
            take(historyPool.loose, target - entries.size, WaveOrigin.HISTORY)
            take(genres.loose, target - entries.size, WaveOrigin.GENRES)
        }

        // ── Фаза 4: чарты — последний фолбэк ────────────────────────────
        if (entries.size < target && !session.radioMode) {
            val charts = splitByLanguage(
                chartsRepository.getTop50().getOrNull().orEmpty(),
                russianPriority,
            )
            take(charts.strict, target - entries.size, WaveOrigin.CATALOG)
            take(charts.loose, target - entries.size, WaveOrigin.CATALOG)
        }

        val orderedEntries = entries.entries
            .shuffled()
            .let { shuffled ->
                if (russianPriority) {
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
            isRussianPriority = russianPriority,
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

    /** Дизлайки пользователя — в волну такие треки не возвращаем. */
    private suspend fun loadDislikedIds(): Set<String> =
        runCatching { dislikedTrackDao.getAll().mapTo(HashSet()) { it.trackId } }
            .rethrowCancellation()
            .getOrDefault(emptySet())

    /** Ключ дедупа: артист и название без регистра, скобок и пунктуации. */
    private fun Track.titleKey(): String =
        normalizeWords(artist) + "|" + normalizeWords(BRACKET_NOISE.replace(title, " "))

    /**
     * Артисты-семена с частотным весом: импортированные лайки (200 треков)
     * дают артисту больший вес, чем редкий трек в истории. Taste-веса
     * добавляются мягко, чтобы не перебить явные лайки.
     */
    private suspend fun buildSeedArtists(
        liked: List<Track>,
        history: List<Track>,
    ): List<String> {
        val scores = LinkedHashMap<String, Pair<String, Double>>()

        fun add(value: String?, weight: Double) {
            val display = value?.trim()?.takeIf { it.isNotBlank() } ?: return
            val key = display.lowercase(Locale.ROOT)
            val current = scores[key]
            scores[key] = if (current == null) {
                display to weight
            } else {
                current.first to (current.second + weight)
            }
        }

        tasteRepository.getTopArtists(SEED_ARTIST_LIMIT).forEach { taste ->
            add(taste.value, taste.weight * TASTE_WEIGHT_FACTOR)
        }
        liked.forEach { add(it.artist, LIKE_WEIGHT) }
        history.forEach { add(it.artist, HISTORY_WEIGHT) }

        return scores.values
            .sortedByDescending { it.second }
            .take(SEED_ARTIST_LIMIT)
            .map { it.first }
    }

    /** Жанры-семена по той же частотной логике, что и артисты. */
    private suspend fun buildSeedGenres(
        liked: List<Track>,
        history: List<Track>,
    ): List<String> {
        val scores = LinkedHashMap<String, Pair<String, Double>>()

        fun add(value: String?, weight: Double) {
            if (!isUsefulGenre(value)) return
            val display = value!!.trim()
            val key = display.lowercase(Locale.ROOT)
            val current = scores[key]
            scores[key] = if (current == null) {
                display to weight
            } else {
                current.first to (current.second + weight)
            }
        }

        tasteRepository.getTopGenres(SEED_GENRE_LIMIT).forEach { taste ->
            add(taste.value, taste.weight * TASTE_WEIGHT_FACTOR)
        }
        liked.forEach { add(it.genre, LIKE_WEIGHT) }
        history.forEach { add(it.genre, HISTORY_WEIGHT) }

        return scores.values
            .sortedByDescending { it.second }
            .take(SEED_GENRE_LIMIT)
            .map { it.first }
    }

    /**
     * Похожие артисты Last.fm ищутся в SoundCloud.
     * SoundCloud по запросу «артист» отдаёт и чужие треки, поэтому совпадение
     * по имени артиста проверяется и такие треки идут первыми.
     */
    private suspend fun loadSimilarTracks(
        seedArtists: List<String>,
        excludedIds: Set<String>,
        russianPriority: Boolean,
    ): WavePool {
        if (seedArtists.isEmpty()) return WavePool()

        val similarArtists = LinkedHashMap<String, String>()
        for (seed in seedArtists.take(SIMILAR_SEED_ARTISTS)) {
            val found = lastFmRepository
                .getSimilarArtists(seed, limit = SIMILAR_ARTISTS_PER_SEED)
                .getOrNull()
                .orEmpty()
            found.forEach { addDistinct(similarArtists, it) }
            if (similarArtists.size >= SIMILAR_ARTISTS_TO_SEARCH) break
        }
        if (similarArtists.isEmpty()) return WavePool()

        // Last.fm часто подмешивает западных артистов к русским семенам:
        // при RU-приоритете сначала ищем тех, чьё имя записано кириллицей
        val orderedArtists = if (russianPriority) {
            similarArtists.values.sortedByDescending { it.containsCyrillic() }
        } else {
            similarArtists.values.toList()
        }

        val candidates = mutableListOf<Candidate>()
        for (artist in orderedArtists.take(SIMILAR_ARTISTS_TO_SEARCH)) {
            val result = soundCloudRepository
                .search(query = artist, limit = SIMILAR_SEARCH_LIMIT)
                .getOrNull()
                .orEmpty()
                .filter { it.id.isNotBlank() && it.id !in excludedIds }

            // Сначала берём треки самого искомого артиста, и только если их нет —
            // всё, что нашлось (каверы, ремиксы, чужие треки по запросу)
            val verified = result
                .filter { artistMatches(it.artist, artist) }
                .take(SIMILAR_TRACKS_PER_ARTIST)
                .map { Candidate(track = it, verified = true) }
            candidates += verified.ifEmpty {
                result
                    .take(SIMILAR_TRACKS_PER_ARTIST)
                    .map { Candidate(track = it, verified = false) }
            }
            if (candidates.size >= SIMILAR_BUFFER_LIMIT) break
        }

        return toPool(rankCandidates(candidates.distinctBy { it.track.id }, russianPriority), russianPriority)
    }

    /**
     * Находки по жанрам: в строгий пул попадают только треки, чей жанр
     * действительно совпал с семенем. Остальное — на самый крайний случай.
     */
    private suspend fun loadGenreTracks(
        seedGenres: List<String>,
        excludedIds: Set<String>,
        russianPriority: Boolean,
    ): WavePool {
        if (seedGenres.isEmpty()) return WavePool()

        val candidates = mutableListOf<Candidate>()
        for (genre in seedGenres.take(GENRE_SEEDS_TO_SEARCH)) {
            val result = soundCloudRepository
                .search(query = genre, limit = GENRE_SEARCH_LIMIT)
                .getOrNull()
                .orEmpty()
                .filter { it.id.isNotBlank() && it.id !in excludedIds }

            // Точное попадание в жанр приоритетнее: SoundCloud по слову «rap»
            // отдаёт и поп, и всё подряд
            val verified = result
                .filter { genreMatches(it.genre, genre) }
                .take(GENRE_TRACKS_PER_SEED)
                .map { Candidate(track = it, verified = true) }
            candidates += verified.ifEmpty {
                result
                    .take(GENRE_TRACKS_PER_SEED)
                    .map { Candidate(track = it, verified = false) }
            }
            if (candidates.size >= GENRE_BUFFER_LIMIT) break
        }

        val ranked = rankCandidates(candidates.distinctBy { it.track.id }, russianPriority)
        val isStrict: (Candidate) -> Boolean = { candidate ->
            candidate.verified && (!russianPriority || candidate.track.hasCyrillic())
        }
        return WavePool(
            strict = ranked.filter(isStrict).map { it.track },
            loose = ranked.filterNot(isStrict).map { it.track },
        )
    }

    /**
     * При RU-приоритете кириллица идёт в строгий пул, латиница — только в запасной:
     * западные треки не должны разбавлять волну, пока хватает своих.
     */
    private fun splitByLanguage(tracks: List<Track>, russianPriority: Boolean): WavePool =
        if (!russianPriority) {
            WavePool(strict = tracks)
        } else {
            WavePool(
                strict = tracks.filter { it.hasCyrillic() },
                loose = tracks.filterNot { it.hasCyrillic() },
            )
        }

    private fun toPool(candidates: List<Candidate>, russianPriority: Boolean): WavePool =
        splitByLanguage(candidates.map { it.track }, russianPriority)

    /**
     * Порядок кандидатов: подтверждённые совпадения раньше остальных,
     * при RU-приоритете кириллица раньше латиницы.
     */
    private fun rankCandidates(
        candidates: List<Candidate>,
        russianPriority: Boolean,
    ): List<Candidate> {
        val tiers = if (russianPriority) {
            val cyrillic = candidates.filter { it.track.hasCyrillic() }
            val latin = candidates.filterNot { it.track.hasCyrillic() }
            listOf(
                cyrillic.filter { it.verified },
                cyrillic.filterNot { it.verified },
                latin.filter { it.verified },
                latin.filterNot { it.verified },
            )
        } else {
            listOf(
                candidates.filter { it.verified },
                candidates.filterNot { it.verified },
            )
        }
        return tiers.flatMap { it.shuffled() }
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

    /** Сравнение артистов без регистра и пунктуации: «Kizaru» == «KIZARU feat. X». */
    private fun artistMatches(trackArtist: String, seedArtist: String): Boolean {
        val track = normalizeWords(trackArtist)
        val seed = normalizeWords(seedArtist)
        if (track.isEmpty() || seed.isEmpty()) return false
        return track == seed || track.contains(seed) || seed.contains(track)
    }

    /**
     * Жанр SoundCloud — свободный текст («Hip-hop & Rap», «Русский рэп»),
     * поэтому сравниваем по словам, переводя частые русские названия в латинские.
     */
    private fun genreMatches(trackGenre: String?, seedGenre: String): Boolean {
        val trackTokens = genreTokens(trackGenre)
        val seedTokens = genreTokens(seedGenre)
        return trackTokens.isNotEmpty() && seedTokens.isNotEmpty() && trackTokens.any { it in seedTokens }
    }

    private fun genreTokens(value: String?): Set<String> =
        normalizeWords(value.orEmpty())
            .split(' ')
            .asSequence()
            .filter { it.length >= MIN_GENRE_TOKEN_LENGTH }
            .map { token -> GENRE_ALIASES[token] ?: token }
            .toSet()

    /** «Music», «Audio», «Podcast» — не жанры, по ним бессмысленно искать. */
    private fun isUsefulGenre(value: String?): Boolean {
        val normalized = normalizeWords(value.orEmpty())
        return normalized.isNotEmpty() && normalized !in JUNK_GENRES
    }

    private fun normalizeWords(value: String): String =
        NON_WORD_CHARS.replace(value.lowercase(Locale.ROOT), " ").trim()

    private fun String.containsCyrillic(): Boolean = any { it in CYRILLIC_RANGE }

    private fun Track.hasCyrillic(): Boolean = "$title $artist".containsCyrillic()

    private fun Track.cyrillicScore(): Int = if (hasCyrillic()) 1 else 0

    /** Кандидат поиска и признак подтверждённого совпадения артиста или жанра. */
    private data class Candidate(
        val track: Track,
        val verified: Boolean,
    )

    /** Кандидаты источника: строгий пул и запасной. */
    private data class WavePool(
        val strict: List<Track> = emptyList(),
        val loose: List<Track> = emptyList(),
    )

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

        /** Веса семян: лайк (в том числе импортированный) важнее истории. */
        const val LIKE_WEIGHT = 1.0
        const val HISTORY_WEIGHT = 0.4
        const val TASTE_WEIGHT_FACTOR = 0.5

        const val SIMILAR_SEED_ARTISTS = 2
        const val SIMILAR_ARTISTS_PER_SEED = 4
        const val SIMILAR_ARTISTS_TO_SEARCH = 5
        const val SIMILAR_SEARCH_LIMIT = 5
        const val SIMILAR_TRACKS_PER_ARTIST = 2
        const val SIMILAR_BUFFER_LIMIT = 12

        /** Сколько похожих артистов берём для радио от трека. */
        const val RADIO_SIMILAR_ARTISTS = 6

        const val GENRE_SEEDS_TO_SEARCH = 2
        const val GENRE_SEARCH_LIMIT = 6
        const val GENRE_TRACKS_PER_SEED = 3
        const val GENRE_BUFFER_LIMIT = 6

        const val MIN_GENRE_TOKEN_LENGTH = 3

        val CYRILLIC_RANGE = '\u0400'..'\u04FF'

        val NON_WORD_CHARS = Regex("[^\\p{L}\\p{N}]+")

        /** Скобочные пометки версии — не часть названия при дедупе. */
        val BRACKET_NOISE = Regex("[(\\[][^)\\]]*[)\\]]")

        /** Служебные подписи вместо жанра */
        val JUNK_GENRES = setOf(
            "music", "музыка", "audio", "аудио", "other", "другое",
            "misc", "podcast", "подкаст", "soundcloud",
        )

        /** Частые русские названия жанров → латинские токены */
        val GENRE_ALIASES = mapOf(
            "рэп" to "rap",
            "реп" to "rap",
            "хип" to "hip",
            "хоп" to "hop",
            "трэп" to "trap",
            "трап" to "trap",
            "хаус" to "house",
            "техно" to "techno",
            "дабстеп" to "dubstep",
            "фонк" to "phonk",
            "рок" to "rock",
            "поп" to "pop",
            "джаз" to "jazz",
            "соул" to "soul",
            "фанк" to "funk",
            "панк" to "punk",
            "метал" to "metal",
            "электро" to "electro",
            "эмбиент" to "ambient",
            "регги" to "reggae",
            "классика" to "classical",
            "диско" to "disco",
        )
    }
}
