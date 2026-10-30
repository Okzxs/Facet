package com.facet.music.feature.home

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/**
 * Параметры одного поискового запроса для fallback.
 *
 * [allowedArtists] — нормализованные имена артистов-сидов. Трек обязан
 * совпасть с полем `artist`, а не с названием. Пустой набор не допускается.
 */
internal data class RecommendationQuery(
    val text: String,
    val allowedArtists: Set<String>,
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
 * Конкретный трек-сид для запроса related-выдачи.
 *
 * [url] — страница трека в YouTube или SoundCloud, по которой NewPipe
 * строит список похожих видео/треков.
 */
internal data class TrackSeed(
    val track: Track,
    val weight: Double,
    val artistKey: String,
) {
    val url: String
        get() = track.transcodingUrl?.takeIf { it.isNotBlank() }
            ?: track.id.takeIf { it.startsWith("http") }
            ?: ""
}

/**
 * Собирает персональные рекомендации: треки-сиды, related-кандидатов,
 * строгий artist-fallback и диверсификацию финальной ленты.
 */
class RecommendationPlanner @Inject constructor() {

    /**
     * Меняется в тестах, чтобы зафиксировать случайную подвыборку.
     */
    internal var random: Random = Random.Default

    /**
     * Строит до восьми артистов-сидов для точечного fallback. Лайки весят
     * больше прослушиваний, свежие записи — больше старых. Один и тот же
     * трек не учитывается дважды.
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
     * Случайно выбирает 4–6 артистов из топ-8. Если артистов меньше, берём всех.
     */
    internal fun selectSeeds(seeds: List<ArtistSeed>): List<ArtistSeed> {
        val pool = seeds.take(MAX_SEED_ARTISTS)
        if (pool.size <= MIN_SEED_SUBSET) return pool.shuffled(random)

        val count = random.nextInt(MIN_SEED_SUBSET, MAX_SEED_SUBSET + 1)
            .coerceAtMost(pool.size)
        return pool.shuffled(random).take(count)
    }

    /**
     * Строит треки-сиды из последних лайков и прослушиваний.
     *
     * Учитываются только треки, для которых можно запросить related-выдачу
     * (YouTube/SoundCloud и URL страницы). Лайк весит в четыре раза больше
     * прослушивания; внутри каждой группы свежие записи получают больший вес.
     */
    internal fun buildTrackSeeds(
        liked: List<Track>,
        history: List<Track>,
    ): List<TrackSeed> {
        val likedTracks = liked.take(PERSONALIZATION_WINDOW)
        val likedIds = likedTracks.mapTo(HashSet()) { it.id }
        val historyTracks = history
            .asSequence()
            .filterNot { it.id in likedIds }
            .take(PERSONALIZATION_WINDOW)
            .toList()

        val seeds = LinkedHashMap<String, TrackSeed>()
        likedTracks.forEachIndexed { index, track ->
            if (track.relatedUrlOrNull() == null) return@forEachIndexed
            seeds[track.id] = TrackSeed(
                track = track,
                weight = LIKE_WEIGHT * recency(index, likedTracks.size),
                artistKey = artistKey(track),
            )
        }
        historyTracks.forEachIndexed { index, track ->
            if (track.relatedUrlOrNull() == null) return@forEachIndexed
            seeds.putIfAbsent(
                track.id,
                TrackSeed(
                    track = track,
                    weight = PLAY_WEIGHT * recency(index, historyTracks.size),
                    artistKey = artistKey(track),
                ),
            )
        }

        return seeds.values.sortedByDescending { it.weight }
    }

    /**
     * Выбирает 4–6 треков-сидов. Сначала старается взять разных артистов,
     * затем добивает подвыборку остальными треками. Финальный порядок случайный,
     * но приоритет лайков и свежих записей сохраняется за счёт пула топ-20.
     */
    internal fun selectTrackSeeds(seeds: List<TrackSeed>): List<TrackSeed> {
        val pool = seeds.take(TRACK_SEED_POOL)
        if (pool.isEmpty()) return emptyList()

        val target = if (pool.size <= MIN_TRACK_SEEDS) {
            pool.size
        } else {
            random.nextInt(MIN_TRACK_SEEDS, MAX_TRACK_SEEDS + 1)
                .coerceAtMost(pool.size)
        }

        val selected = ArrayList<TrackSeed>(target)
        val selectedIds = HashSet<String>()
        val selectedArtists = HashSet<String>()

        for (seed in pool.shuffled(random)) {
            if (selected.size >= target) break
            if (!selectedArtists.add(seed.artistKey)) continue
            if (!selectedIds.add(seed.track.id)) continue
            selected += seed
        }
        for (seed in pool.shuffled(random)) {
            if (selected.size >= target) break
            if (selectedIds.add(seed.track.id)) selected += seed
        }

        return selected.shuffled(random)
    }

    /**
     * Артисты, которые заметно доминируют в персональной выборке. Для них
     * допускается один дополнительный трек в финальной ленте.
     */
    internal fun findDominantArtists(
        liked: List<Track>,
        history: List<Track>,
    ): Set<String> {
        val personal = (liked.take(PERSONALIZATION_WINDOW) + history.take(PERSONALIZATION_WINDOW))
            .distinctBy { it.id }
        val counts = personal
            .mapNotNull { track ->
                normalizeArtist(track.artist)
                    .takeIf { it.isNotBlank() && it != UNKNOWN_ARTIST }
            }
            .groupingBy { it }
            .eachCount()
        val total = counts.values.sum()
        if (total == 0) return emptySet()

        return counts
            .filterValues { count ->
                count >= DOMINANT_ARTIST_MIN_TRACKS &&
                    count.toDouble() / total >= DOMINANT_ARTIST_MIN_SHARE
            }
            .keys
    }

    /**
     * Строит точечные fallback-запросы только по топ-артистам из лайков и
     * истории. Глобальный популярный запрос здесь не добавляется.
     */
    internal fun buildArtistFallbackQueries(
        liked: List<Track>,
        history: List<Track>,
    ): List<RecommendationQuery> {
        val seeds = buildArtistSeeds(liked, history)
        if (seeds.isEmpty()) return emptyList()

        val queries = mutableListOf<RecommendationQuery>()
        val seenQueries = HashSet<String>()

        selectSeeds(seeds).forEach { seed ->
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

        return queries
    }

    /**
     * Строгая проверка для fallback: сравниваем только поле `artist`.
     * Название трека не является доказательством принадлежности артисту.
     */
    internal fun matchesExactArtist(
        track: Track,
        allowedArtists: Set<String>,
    ): Boolean {
        if (allowedArtists.isEmpty()) return false

        val trackArtist = normalizeArtist(track.artist)
        return trackArtist.isNotBlank() &&
            trackArtist != UNKNOWN_ARTIST &&
            trackArtist in allowedArtists
    }

    /**
     * Убирает из кандидатов треки, которые пользователь отметил дизлайком.
     * Фильтр применяется до отбора, чтобы дизлайки не занимали слоты ленты.
     */
    internal fun filterDisliked(
        candidates: List<Track>,
        dislikedTrackIds: Set<String>,
    ): List<Track> {
        if (dislikedTrackIds.isEmpty()) return candidates
        return candidates.filter { it.id !in dislikedTrackIds }
    }

    /**
     * Убирает дубликаты одного и того же трека из разных загрузок.
     *
     * YouTube отдаёт каждый перезалив отдельным id, поэтому дедуп по id
     * пропускал одинаковые песни. Сравниваем нормализованные «артист + название».
     */
    internal fun dedupeByTitle(tracks: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return tracks.filter { track ->
            val key = track.matchKey()
            key.isBlank() || seen.add(key)
        }
    }

    /** Ключ сравнения: артист без feat./prod. + название без скобочных пометок. */
    internal fun Track.matchKey(): String {
        val artistKeys = normalizeArtist(artist)
        val titleKey = title
            .lowercase(Locale.ROOT)
            .replace(TITLE_NOISE, " ")
            .replace(NON_WORD, " ")
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
        return if (titleKey.isBlank()) "" else "$artistKeys|$titleKey"
    }

    /**
     * Перемешивает кандидатов и набирает не больше [limit] треков.
     * Обычный кап — [maxPerArtist], для доминирующих артистов —
     * [maxPerDominantArtist].
     */
    internal fun selectTracks(
        candidates: List<Track>,
        limit: Int = RECOMMENDATION_TRACKS_LIMIT,
        maxPerArtist: Int = MAX_TRACKS_PER_ARTIST,
        dominantArtists: Set<String> = emptySet(),
        maxPerDominantArtist: Int = MAX_TRACKS_PER_DOMINANT_ARTIST,
    ): List<Track> {
        val selected = ArrayList<Track>(limit)
        val selectedIds = HashSet<String>()
        val artistCounts = HashMap<String, Int>()

        for (track in candidates.shuffled(random)) {
            if (selected.size >= limit) break
            if (track.id in selectedIds) continue

            val artistKey = artistKey(track)
            val count = artistCounts[artistKey] ?: 0
            val allowed = perArtistLimit(
                track = track,
                maxPerArtist = maxPerArtist,
                maxPerDominantArtist = maxPerDominantArtist,
                dominantArtists = dominantArtists,
            )
            if (count >= allowed) continue

            selected += track
            selectedIds += track.id
            artistCounts[artistKey] = count + 1
        }

        return selected
    }

    /**
     * Добивает готовую ленту fallback-кандидатами с теми же ограничениями
     * и перемешивает итог.
     */
    internal fun appendTracks(
        selected: List<Track>,
        candidates: List<Track>,
        limit: Int = RECOMMENDATION_TRACKS_LIMIT,
        maxPerArtist: Int = MAX_TRACKS_PER_ARTIST,
        dominantArtists: Set<String> = emptySet(),
        maxPerDominantArtist: Int = MAX_TRACKS_PER_DOMINANT_ARTIST,
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
            val allowed = perArtistLimit(
                track = track,
                maxPerArtist = maxPerArtist,
                maxPerDominantArtist = maxPerDominantArtist,
                dominantArtists = dominantArtists,
            )
            if (count >= allowed) continue

            result += track
            selectedIds += track.id
            artistCounts[artistKey] = count + 1
        }

        return result.shuffled(random)
    }

    private fun perArtistLimit(
        track: Track,
        maxPerArtist: Int,
        maxPerDominantArtist: Int,
        dominantArtists: Set<String>,
    ): Int {
        val normalized = normalizeArtist(track.artist)
        return if (normalized in dominantArtists) {
            maxPerDominantArtist
        } else {
            maxPerArtist
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

    private fun Track.relatedUrlOrNull(): String? {
        if (source != Source.YOUTUBE && source != Source.SOUNDCLOUD) return null
        return transcodingUrl?.takeIf { it.isNotBlank() }
            ?: id.takeIf { it.startsWith("http") }
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
        const val MAX_SEED_SUBSET = 6
        const val PERSONALIZATION_WINDOW = 20
        const val LIKE_WEIGHT = 4.0
        const val PLAY_WEIGHT = 1.0
        const val RECENCY_DECAY = 0.12
        const val TRACK_SEED_POOL = 20
        const val MIN_TRACK_SEEDS = 4
        const val MAX_TRACK_SEEDS = 6
        const val RECOMMENDATION_TRACKS_LIMIT = 15
        const val MAX_TRACKS_PER_ARTIST = 2
        const val MAX_TRACKS_PER_DOMINANT_ARTIST = 3
        const val DOMINANT_ARTIST_MIN_TRACKS = 3
        const val DOMINANT_ARTIST_MIN_SHARE = 0.25
        const val UNKNOWN_ARTIST = "unknown"
    }
}

internal const val POPULAR_QUERY = "popular songs official audio"

private val WHITESPACE_PATTERN = Regex("\\s+")

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Скобочные пометки версии: «(Official Video)», «[Remastered]», «— Live». */
private val TITLE_NOISE = Regex("[(\\[][^)\\]]*[)\\]]")

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

private val ARTIST_CREDITS_PATTERN = Regex(
    pattern = """(?:^|[\s(\[]+)(?:feat|ft|featuring|prod|produced)\b\.?\s*(?:by\s+)?.*$""",
    option = RegexOption.IGNORE_CASE,
)
