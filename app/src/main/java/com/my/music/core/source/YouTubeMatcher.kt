package com.my.music.core.source

import com.my.music.data.model.Track
import java.util.Locale
import javax.inject.Inject

/**
 * Сопоставление трека из внешнего источника с находками YouTube.
 *
 * Нужно, когда у источника нет своей ссылки на аудио (Spotify, Apple без превью,
 * истёкший preview-URL). Ошибка матчинга дороже отказа: чужой трек в плеере
 * хуже, чем честное «не найдено», поэтому есть порог [MIN_SCORE].
 */
class YouTubeMatcher @Inject constructor() {

    /**
     * Итоговый балл кандидата. Положительные признаки — совпадение артиста,
     * названия и длительности; отрицательные — каверы/ремиксы/замедления.
     */
    fun score(original: Track, candidate: Track): Double {
        val originalTitle = normalize(original.title)
        val candidateTitle = normalize(candidate.title)
        val candidateArtist = normalize(candidate.artist)
        val originalArtist = normalize(original.artist)

        var score = 0.0

        // Артист: в названии ролика или отдельным полем.
        if (originalArtist.isNotBlank()) {
            if (candidateArtist.contains(originalArtist) || originalArtist.contains(candidateArtist)) {
                score += ARTIST_MATCH
            } else if (candidateTitle.contains(originalArtist)) {
                score += ARTIST_IN_TITLE
            } else {
                score -= ARTIST_MISMATCH
            }
        }

        // Название: доля совпавших слов заголовка.
        score += TITLE_MATCH * titleSimilarity(originalTitle, candidateTitle)

        // Длительность: главный признак студийной версии.
        if (original.durationMs > 0 && candidate.durationMs > 0) {
            val deltaSeconds = kotlin.math.abs(original.durationMs - candidate.durationMs) / 1000.0
            score += when {
                deltaSeconds <= 3.0 -> DURATION_CLOSE
                deltaSeconds <= 8.0 -> DURATION_NEAR
                deltaSeconds > 25.0 -> DURATION_FAR
                else -> 0.0
            }
        }

        // Явные маркеры «не студийной» версии.
        val haystack = "$candidateTitle $candidateArtist"
        if (BAD_MARKERS.any { haystack.contains(it) }) score -= BAD_MARKER_PENALTY
        if (GOOD_MARKERS.any { haystack.contains(it) }) score += GOOD_MARKER_BONUS

        // Совпавший ISRC в описании — почти безошибочный признак.
        original.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            if (candidate.title.contains(isrc, ignoreCase = true)) score += ISRC_MATCH
        }

        return score
    }

    /** Лучший кандидат или null, если никто не прошёл порог. */
    fun best(original: Track, candidates: List<Track>): Track? = candidates
        .asSequence()
        .filter { it.durationMs <= 0 || it.durationMs in MIN_DURATION_MS..MAX_DURATION_MS }
        .map { it to score(original, it) }
        .filter { (_, score) -> score >= MIN_SCORE }
        .maxByOrNull { it.second }
        ?.first

    private fun titleSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val common = left.count { it in right }
        return common.toDouble() / left.size.coerceAtLeast(1)
    }

    private fun normalize(raw: String): String = raw
        .lowercase(Locale.ROOT)
        .replace(BRACKETS, " ")
        .replace(NON_WORD, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        const val ARTIST_MATCH = 3.0
        const val ARTIST_IN_TITLE = 2.0
        const val ARTIST_MISMATCH = 1.0
        const val TITLE_MATCH = 3.0
        const val DURATION_CLOSE = 2.0
        const val DURATION_NEAR = 1.0
        const val DURATION_FAR = -3.0
        const val BAD_MARKER_PENALTY = 6.0
        const val GOOD_MARKER_BONUS = 1.0
        const val ISRC_MATCH = 4.0

        /** Ниже этого балла матч считается ненадёжным. */
        const val MIN_SCORE = 5.0

        const val MIN_DURATION_MS = 45_000L
        const val MAX_DURATION_MS = 900_000L

        val BAD_MARKERS = listOf(
            "cover", "кавер", "remix", "ремикс", "slowed", "speed up", "sped up",
            "nightcore", "karaoke", "караоке", "instrumental", "минус", "live",
            "reaction", "tutorial", "8d",
        )
        val GOOD_MARKERS = listOf("official", "topic", "audio")

        val BRACKETS = Regex("[()\\[\\]{}]")
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")
    }
}
