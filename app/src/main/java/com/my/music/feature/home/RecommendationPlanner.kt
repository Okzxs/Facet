package com.my.music.feature.home

import com.my.music.data.model.Track
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/**
 * Параметры одной поисковой выдачи.
 *
 * [allowedArtists] — нормализованные артисты-сиды, которым должен соответствовать
 * трек. `null` означает общий популярный запрос без привязки к сидам.
 */
internal data class RecommendationQuery(
    val text: String,
    val allowedArtists: Set<String>?,
)

/**
 * Артист-сид с накопленным весом из лайков и истории прослушиваний.
 */
internal data class ArtistSeed(
    val name: String,
    val normalizedName: String,
    val weight: Double,
    val genre: String?,
)

/**
 * Собирает персональные рекомендации: сиды, поисковые запросы, проверку
 * артиста и диверсификацию финальной ленты.
 */
class RecommendationPlanner @Inject constructor() {

    /**
     * Меняется в тестах, чтобы зафиксировать случайную подвыборку.
     */
    internal var random: Random = Random.Default

    /**
     * Строит до восьми артистов-сидов. Лайки весят больше прослушиваний,
     * свежие записи — больше старых. Один и тот же трек не учитывается дважды.
     */
    internal fun buildArtistSeeds(
        liked: List<Track>,
        history: List<Track>,
    ): List<ArtistSeed> {
        val likedTracks = liked.take(PERSONALIZATION_WINDOW)
        val likedIds = likedTracks.mapTo(HashSet()) { it.id }
        val historyTracks = history
            .asSequence()
            .filterNot { it.id in likedIds }
            .take(PERSONALIZATION_WINDOW)
            .toList()

        val weights = mutableMapOf<String, SeedAccumulator>()
        likedTracks.forEachIndexed { index, track ->
            accumulate(
                weights = weights,
                track = track,
                weight = LIKE_WEIGHT * recency(index, likedTracks.size),
            )
        }
        historyTracks.forEachIndexed { index, track ->
            accumulate(
                weights = weights,
                track = track,
                weight = PLAY_WEIGHT * recency(index, historyTracks.size),
            )
        }

        return weights.values
            .sortedByDescending { it.weight }
            .take(MAX_SEED_ARTISTS)
            .map {
                ArtistSeed(
                    name = it.name,
                    normalizedName = it.normalizedName,
                    weight = it.weight,
                    genre = it.genre,
                )
            }
    }

    /**
     * Случайно выбирает 4–5 артистов из топ-8. Если артистов меньше, берём всех.
     */
    internal fun selectSeeds(seeds: List<ArtistSeed>): List<ArtistSeed> {
        val pool = seeds.take(MAX_SEED_ARTISTS)
        if (pool.size <= MIN_SEED_SUBSET) return pool.shuffled(random)

        val count = random.nextInt(MIN_SEED_SUBSET, MAX_SEED_SUBSET + 1)
            .coerceAtMost(pool.size)
        return pool.shuffled(random).take(count)
    }

    /**
     * Генерирует запросы для текущего refresh. На каждого выбранного артиста —
     * 1–2 случайных варианта, плюс 1–2 общих жанровых запроса из истории.
     */
    internal fun buildQueries(
        liked: List<Track>,
        history: List<Track>,
    ): List<RecommendationQuery> {
        val seeds = buildArtistSeeds(liked, history)
        if (seeds.isEmpty()) {
            return listOf(RecommendationQuery(POPULAR_QUERY, allowedArtists = null))
        }

        val selectedSeeds = selectSeeds(seeds)
        val allSeedNames = seeds.mapTo(HashSet()) { it.normalizedName }
        val queries = mutableListOf<RecommendationQuery>()
        val seenQueries = HashSet<String>()

        selectedSeeds.forEach { seed ->
            val variants = buildList {
                add("${seed.name} official audio")
                seed.genre?.let { genre -> add("${seed.name} $genre") }
                add("${seed.name} official")
            }.distinct()
            val queryCount = if (variants.size == 1) 1 else random.nextInt(1, 3)
            variants.shuffled(random).take(queryCount).forEach { text ->
                if (seenQueries.add(text.lowercase(Locale.ROOT))) {
                    queries += RecommendationQuery(
                        text = text,
                        allowedArtists = setOf(seed.normalizedName),
                    )
                }
            }
        }

        buildGenreQueries(liked, history, allSeedNames).forEach { query ->
            if (seenQueries.add(query.text.lowercase(Locale.ROOT))) {
                queries += query
            }
        }

        return queries.ifEmpty {
            listOf(RecommendationQuery(POPULAR_QUERY, allowedArtists = null))
        }
    }

    /**
     * Проверяет, что трек принадлежит одному из сид-артистов.
     *
     * Если NewPipe не отдал артиста (в том числе подставил `Unknown`),
     * допускаем совпадение только по началу названия — не по подстроке.
     */
    internal fun matchesSeedArtist(
        track: Track,
        allowedArtists: Set<String>,
    ): Boolean {
        if (allowedArtists.isEmpty()) return false

        val trackArtist = normalizeArtist(track.artist)
        if (trackArtist.isNotBlank() && trackArtist != UNKNOWN_ARTIST) {
            return trackArtist in allowedArtists
        }

        val title = normalizeForMatch(track.title)
        return allowedArtists.any { seed -> title.startsWith(seed) }
    }

    /**
     * Перемешивает кандидатов и набирает не больше [limit] треков,
     * оставляя максимум [maxPerArtist] записей на одного артиста.
     */
    internal fun selectTracks(
        candidates: List<Track>,
        limit: Int = RECOMMENDATION_TRACKS_LIMIT,
        maxPerArtist: Int = MAX_TRACKS_PER_ARTIST,
    ): List<Track> {
        val selected = ArrayList<Track>(limit)
        val selectedIds = HashSet<String>()
        val artistCounts = HashMap<String, Int>()

        for (track in candidates.shuffled(random)) {
            if (selected.size >= limit) break
            if (track.id in selectedIds) continue

            val artistKey = artistKey(track)
            val count = artistCounts[artistKey] ?: 0
            if (count >= maxPerArtist) continue

            selected += track
            selectedIds += track.id
            artistCounts[artistKey] = count + 1
        }

        return selected
    }

    /**
     * Добивает готовую ленту популярными треками с теми же ограничениями.
     */
    internal fun appendTracks(
        selected: List<Track>,
        candidates: List<Track>,
        limit: Int = RECOMMENDATION_TRACKS_LIMIT,
        maxPerArtist: Int = MAX_TRACKS_PER_ARTIST,
    ): List<Track> {
        val result = selected.toMutableList()
        val selectedIds = result.mapTo(HashSet()) { it.id }
        val artistCounts = result
            .groupingBy { artistKey(it) }
            .eachCount()
            .toMutableMap()

        for (track in candidates.shuffled(random)) {
            if (result.size >= limit) break
            if (track.id in selectedIds) continue

            val artistKey = artistKey(track)
            val count = artistCounts[artistKey] ?: 0
            if (count >= maxPerArtist) continue

            result += track
            selectedIds += track.id
            artistCounts[artistKey] = count + 1
        }

        return result
    }

    private fun buildGenreQueries(
        liked: List<Track>,
        history: List<Track>,
        allowedArtists: Set<String>,
    ): List<RecommendationQuery> {
        val historyGenres = history
            .take(PERSONALIZATION_WINDOW)
            .mapNotNull { track -> track.genre?.trim()?.takeIf { it.isNotEmpty() } }
        val likedGenres = liked
            .take(PERSONALIZATION_WINDOW)
            .mapNotNull { track -> track.genre?.trim()?.takeIf { it.isNotEmpty() } }
        val genres = (historyGenres.ifEmpty { likedGenres })
            .distinctBy { it.lowercase(Locale.ROOT) }

        if (genres.isEmpty()) return emptyList()

        val queryCount = if (genres.size == 1) 1 else random.nextInt(1, 3)
        return genres.shuffled(random)
            .take(queryCount)
            .map { genre ->
                RecommendationQuery(
                    text = "$genre official audio",
                    allowedArtists = allowedArtists,
                )
            }
    }

    private fun accumulate(
        weights: MutableMap<String, SeedAccumulator>,
        track: Track,
        weight: Double,
    ) {
        val normalized = normalizeArtist(track.artist)
        if (normalized.isBlank() || normalized == UNKNOWN_ARTIST) return

        val displayName = stripArtistCredits(track.artist)
        if (displayName.isBlank()) return

        val accumulator = weights.getOrPut(normalized) {
            SeedAccumulator(
                name = displayName,
                normalizedName = normalized,
            )
        }
        accumulator.weight += weight
        if (accumulator.genre.isNullOrBlank()) {
            accumulator.genre = track.genre?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun recency(index: Int, size: Int): Double {
        if (size <= 1) return 1.0
        return 1.0 / (1.0 + index * RECENCY_DECAY)
    }

    private fun artistKey(track: Track): String {
        val normalized = normalizeArtist(track.artist)
        return if (normalized.isNotBlank() && normalized != UNKNOWN_ARTIST) {
            "artist:$normalized"
        } else {
            // Без артиста не даём разным трекам схлопнуться в один «Unknown».
            "track:${track.id}"
        }
    }

    private data class SeedAccumulator(
        val name: String,
        val normalizedName: String,
        var weight: Double = 0.0,
        var genre: String? = null,
    )

    private companion object {
        const val MAX_SEED_ARTISTS = 8
        const val MIN_SEED_SUBSET = 4
        const val MAX_SEED_SUBSET = 5
        const val PERSONALIZATION_WINDOW = 20
        const val LIKE_WEIGHT = 4.0
        const val PLAY_WEIGHT = 1.0
        const val RECENCY_DECAY = 0.12
        const val RECOMMENDATION_TRACKS_LIMIT = 15
        const val MAX_TRACKS_PER_ARTIST = 3
        const val UNKNOWN_ARTIST = "unknown"
    }
}

internal const val POPULAR_QUERY = "popular songs official audio"

private val WHITESPACE_PATTERN = Regex("\\s+")

/**
 * Убирает feat./prod. и всё, что идёт после них, схлопывает пробелы
 * и приводит имя к нижнему регистру.
 */
internal fun normalizeArtist(raw: String): String {
    if (raw.isBlank()) return ""
    return stripArtistCredits(raw)
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
        .lowercase(Locale.ROOT)
}

private fun stripArtistCredits(raw: String): String =
    raw.replace(ARTIST_CREDITS_PATTERN, "")

private fun normalizeForMatch(raw: String): String =
    raw.replace(WHITESPACE_PATTERN, " ")
        .trim()
        .lowercase(Locale.ROOT)

private val ARTIST_CREDITS_PATTERN = Regex(
    pattern = """(?:^|[\s(\[]+)(?:feat|ft|featuring|prod|produced)\b\.?\s*(?:by\s+)?.*$""",
    option = RegexOption.IGNORE_CASE,
)
