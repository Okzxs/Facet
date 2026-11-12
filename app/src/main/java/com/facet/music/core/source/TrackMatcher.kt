package com.facet.music.core.source

import com.facet.music.data.model.Track
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class TrackMatch(
    val track: Track,
    val score: Double,
    val confidence: Double,
)

class TrackMatcher @Inject constructor() {

    fun score(original: Track, candidate: Track): Double {
        val originalTitle = normalize(original.title)
        val candidateTitle = normalize(candidate.title)
        val originalArtist = normalize(original.artist)
        val candidateArtist = normalize(candidate.artist)

        if (!durationFits(original.durationMs, candidate.durationMs)) return 0.0

        var score = 0.0

        if (originalArtist.isNotBlank()) {
            when {
                candidateArtist.contains(originalArtist) ||
                    originalArtist.contains(candidateArtist) -> score += ARTIST_MATCH

                candidateTitle.contains(originalArtist) -> score += ARTIST_IN_TITLE
                else -> score -= ARTIST_MISMATCH
            }
        }

        score += TITLE_MATCH * titleSimilarity(originalTitle, candidateTitle)

        if (original.durationMs > 0 && candidate.durationMs > 0) {
            val deltaSeconds = abs(original.durationMs - candidate.durationMs) / 1000.0
            score += when {
                deltaSeconds <= 2.0 -> DURATION_CLOSE
                deltaSeconds <= MAX_DURATION_DELTA_SECONDS -> DURATION_NEAR
                else -> DURATION_FAR
            }
        }

        val originalHaystack = "$originalTitle $originalArtist"
        val candidateHaystack = "$candidateTitle $candidateArtist"
        val markerPenalty = BAD_MARKERS
            .filter { marker ->
                candidateHaystack.contains(marker) && !originalHaystack.contains(marker)
            }
            .sumOf { marker ->
                when (marker) {
                    "live" -> LIVE_PENALTY
                    "cover", "remix", "nightcore", "karaoke" -> VERSION_PENALTY
                    else -> SPEED_PENALTY
                }
            }
        score -= markerPenalty.coerceAtMost(MAX_MARKER_PENALTY)

        if (GOOD_MARKERS.any { candidateHaystack.contains(it) }) score += GOOD_MARKER_BONUS

        original.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            if (candidate.title.contains(isrc, ignoreCase = true)) score += ISRC_MATCH
        }

        return score
    }

    fun match(original: Track, candidate: Track): TrackMatch? {
        if (candidate.durationMs > 0 &&
            candidate.durationMs !in MIN_DURATION_MS..MAX_DURATION_MS
        ) {
            return null
        }
        if (!durationFits(original.durationMs, candidate.durationMs)) return null
        val score = score(original, candidate)
        if (score < MIN_SCORE) return null
        return TrackMatch(
            track = candidate,
            score = score,
            confidence = (score / MAX_SCORE).coerceIn(0.0, 1.0),
        )
    }

    fun best(original: Track, candidates: List<Track>): TrackMatch? = candidates
        .asSequence()
        .mapNotNull { match(original, it) }
        .maxByOrNull { it.score }

    private fun durationFits(originalMs: Long, candidateMs: Long): Boolean {
        if (originalMs <= 0L || candidateMs <= 0L) return true
        return abs(originalMs - candidateMs) <= MAX_DURATION_DELTA_MS
    }

    private fun titleSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val left = a.split(' ').filter { it.isNotBlank() }.toSet()
        val right = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.count { it in right }.toDouble() / left.size
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
        const val VERSION_PENALTY = 5.0
        const val LIVE_PENALTY = 4.0
        const val SPEED_PENALTY = 4.0
        const val MAX_MARKER_PENALTY = 8.0
        const val GOOD_MARKER_BONUS = 1.0
        const val ISRC_MATCH = 4.0
        const val MAX_SCORE = 14.0
        const val MIN_SCORE = 5.0

        const val MAX_DURATION_DELTA_SECONDS = 7.0
        const val MAX_DURATION_DELTA_MS = 7_000L
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
