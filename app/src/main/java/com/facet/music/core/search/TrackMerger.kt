package com.facet.music.core.search

import com.facet.music.data.model.Track
import java.util.Locale

/**
 * Слияние выдачи нескольких источников для сводного поиска.
 *
 * Треки считаются одним и тем же, если совпали нормализованные артист и
 * название — регистр, пунктуация и скобочные пометки версии не важны.
 * В результат попадает «лучший» вариант: с полным потоком, а не 30-секундным
 * превью; [Track.sourceCount] хранит число источников, где трек нашёлся.
 */
object TrackMerger {

    /** Скобки, в которых обычно живут версии: «(Remix)», «[Live]», «(feat. X)». */
    private val BRACKETS = Regex("[(\\[][^)\\]]*[)\\]]")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /**
     * [groups] — выдача каждого источника в порядке приоритета.
     * Ранжирование: точный артист, все токены запроса, позиция в источнике,
     * число источников и штраф за версии. Сортировка стабильная.
     */
    fun merge(groups: List<List<Track>>, query: String = ""): List<Track> {
        val buckets = LinkedHashMap<String, MutableList<RankedTrack>>()
        groups.forEach { tracks ->
            tracks.forEachIndexed { rank, track ->
                if (track.id.isBlank() || track.title.isBlank()) return@forEachIndexed
                buckets.getOrPut(key(track)) { mutableListOf() }
                    .add(RankedTrack(track = track, rank = rank))
            }
        }
        val queryTokens = queryTokens(query)
        val normalizedQuery = ArtistMerger.normalize(query)
        return buckets.values
            .map { candidates ->
                val best = preferred(candidates)
                ScoredTrack(
                    track = best.track.copy(
                        sourceCount = candidates.map { it.track.source }.distinct().size,
                    ),
                    score = score(candidates, best, queryTokens, normalizedQuery),
                )
            }
            .sortedByDescending { it.score }
            .map { it.track }
    }

    /**
     * Слияние без перестановки уже показанных строк: сводный поиск получает
     * источники по одному, и новые треки должны добавляться в конец, а не
     * поднимать всю выдачу и сбрасывать позицию скролла.
     */
    fun mergeKeepingOrder(
        existing: List<Track>,
        groups: List<List<Track>>,
        query: String = "",
    ): List<Track> {
        val merged = merge(groups, query)
        if (existing.isEmpty()) return merged

        val byKey = merged.associateBy { key(it) }
        val kept = existing.mapNotNull { byKey[key(it)] }
        val keptKeys = kept.mapTo(HashSet()) { key(it) }
        return kept + merged.filter { key(it) !in keptKeys }
    }

    /** Ключ дедупа: артист + название без регистра и скобочных пометок. */
    fun key(track: Track): String =
        normalize(track.artist) + "|" + normalize(BRACKETS.replace(track.title, " "))

    private fun preferred(candidates: List<RankedTrack>): RankedTrack =
        candidates.firstOrNull {
            !hasVersionWord(it.track) &&
                it.track.previewSeconds == null && !it.track.isSubscriptionOnly
        }
            ?: candidates.firstOrNull {
                it.track.streamUrl != null && it.track.previewSeconds == null &&
                    !it.track.isSubscriptionOnly
            }
            ?: candidates.firstOrNull {
                it.track.previewSeconds == null && !it.track.isSubscriptionOnly
            }
            ?: candidates.firstOrNull { it.track.previewSeconds == null }
            ?: candidates.first()

    private fun score(
        candidates: List<RankedTrack>,
        best: RankedTrack,
        queryTokens: List<String>,
        normalizedQuery: String,
    ): Double {
        val track = best.track
        var score = 0.0
        if (normalizedQuery.isNotBlank() && ArtistMerger.normalize(track.artist) == normalizedQuery) {
            score += SearchConfig.EXACT_ARTIST_BOOST
        }
        if (queryTokens.isNotEmpty()) {
            val haystack = normalize("${track.artist} ${track.title}")
            if (queryTokens.all { haystack.contains(it) }) score += SearchConfig.ALL_TOKENS_BOOST
        }
        score += 1.0 / (1.0 + best.rank)
        val sourceCount = candidates.map { it.track.source }.distinct().size
        if (sourceCount > 1) score += (sourceCount - 1) * SearchConfig.EXTRA_SOURCE_BOOST
        if (hasVersionWord(track) && queryTokens.none { token -> token in SearchConfig.VERSION_WORDS }) {
            score -= SearchConfig.VERSION_PENALTY
        }
        return score
    }

    private fun hasVersionWord(track: Track): Boolean {
        val value = normalize("${track.title} ${track.genre.orEmpty()}")
        return SearchConfig.VERSION_WORDS.any { marker -> value.contains(marker) }
    }

    private fun queryTokens(query: String): List<String> = ArtistMerger.tokens(query)

    private fun normalize(value: String): String =
        NON_WORD.replace(value.lowercase(Locale.ROOT), " ").trim()

    private data class RankedTrack(
        val track: Track,
        val rank: Int,
    )

    private data class ScoredTrack(
        val track: Track,
        val score: Double,
    )
}
