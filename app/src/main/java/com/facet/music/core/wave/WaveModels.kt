package com.facet.music.core.wave

import com.facet.music.data.model.Track
import kotlin.random.Random

/** Источник времени; в тестах подменяется фиксированными часами. */
interface Clock {
    fun nowMs(): Long
}

/** Фабрика случайности сессии: один seed на сессию, отдельные потоки источникам. */
interface RandomProvider {
    fun newSeed(): Long
    fun create(seed: Long): Random
}

enum class WaveMood(
    val familiarShare: Double,
    val explorationShare: Double,
) {
    FAMILIAR(0.15, 0.10),
    BALANCE(0.08, 0.20),
    DISCOVERY(0.03, 0.35);

    companion object {
        fun fromName(raw: String?): WaveMood =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BALANCE
    }
}

enum class CandidateOrigin(val prior: Double, val exploration: Boolean) {
    RELATED(1.0, false),
    SIMILAR_ARTIST(0.8, false),
    FOLLOWED(0.8, false),
    TAG(0.6, true),
    SECOND_HOP(0.5, true),
    FAMILIAR(0.4, false),
}

/** Кандидат конкретного источника. */
data class Candidate(
    val track: Track,
    val origin: CandidateOrigin,
    val seedKey: String? = null,
    val sourceRank: Int = 0,
)

data class SeedArtist(
    val key: String,
    val displayName: String,
    val weight: Double,
)

/** Трек-семя с уже посчитанным весом (свежесть × штраф прошлых сессий). */
data class TrackSeed(
    val track: Track,
    val weight: Double,
    val trackKey: String,
    val artistKey: String,
)

data class WaveTasteProfile(
    val artists: Map<String, Double> = emptyMap(),
    val genres: Map<String, Double> = emptyMap(),
    val likedMarkers: Set<String> = emptySet(),
)

/** Трек, который пользователь уже слушал или лайкнул. */
data class PlayedTrack(
    val track: Track,
    val atMs: Long,
    val playedFraction: Float?,
    val liked: Boolean,
)

data class WaveImpression(
    val trackKey: String,
    val trackId: String,
    val artistKey: String,
    val sessionId: String,
    val origin: CandidateOrigin,
    val seedKey: String?,
    val shownAt: Long,
    val outcome: WaveOutcome,
    val playedMs: Long,
)

enum class WaveOutcome { SHOWN, PLAYED, SKIPPED, FINISHED, LIKED, DISLIKED }

data class WaveCandidateDetails(
    val trackKey: String,
    val origin: CandidateOrigin,
    val seedKey: String?,
    val artistAffinity: Double,
    val genreAffinity: Double,
    val originPrior: Double,
    val novelty: Double,
    val seedWeight: Double,
    val popularity: Double,
    val penalty: Double,
    val noise: Double,
    val score: Double,
)

data class WaveHistory(
    val impressions: List<WaveImpression> = emptyList(),
    val plays: List<PlayedTrack> = emptyList(),
    val familiarTracks: List<PlayedTrack> = emptyList(),
    val dislikedTrackIds: Set<String> = emptySet(),
    val dislikedTrackKeys: Set<String> = emptySet(),
    val blockedArtistKeys: Set<String> = emptySet(),
    val queueTrackIds: Set<String> = emptySet(),
    val queueTrackKeys: Set<String> = emptySet(),
)

/** Состояние сессии, которое нужно сохранять между пачками. */
data class WaveSessionPlan(
    val sessionId: String,
    val createdAt: Long,
    val randomSeed: Long,
    val profile: WaveTasteProfile,
    val history: WaveHistory,
    val seedArtists: List<SeedArtist>,
    val seedGenres: List<String>,
    val seedTracks: List<TrackSeed>,
    val previousSeedKeys: Set<String>,
    val previousSeedArtistKeys: Set<String> = emptySet(),
    val excludedTrackKeys: Set<String> = emptySet(),
    val isRadio: Boolean = false,
    val radioSeed: Track? = null,
    val radioArtistKey: String? = null,
    val mood: WaveMood = WaveMood.BALANCE,
)

data class WaveSessionRecord(
    val sessionId: String,
    val createdAt: Long,
    val randomSeed: Long,
    val mode: String,
    val seedArtistKeys: List<String>,
    val seedTrackKeys: List<String>,
    val isRadio: Boolean,
    val radioSeed: String?,
    val cursor: Int,
    val mood: String = WaveMood.BALANCE.name,
    val radioArtistKey: String? = null,
    val seedTracksJson: String? = null,
)

data class WaveSessionState(
    val recentArtistKeys: List<String> = emptyList(),
    val recentSeedKeys: List<String> = emptyList(),
    val batchNumber: Int = 0,
    val shownTrackKeys: Set<String> = emptySet(),
)

data class WaveGeneration(
    val tracks: List<Track>,
    val origins: Map<CandidateOrigin, Int>,
    val details: Map<String, WaveCandidateDetails> = emptyMap(),
    val state: WaveSessionState,
    val requestedSize: Int,
) {
    val isShort: Boolean get() = tracks.size < requestedSize
}

data class WaveConfig(
    val startBatchSize: Int = 20,
    val refillBatchSize: Int = 10,
    val maxBatchSize: Int = 30,
    val familiarShare: Double = 0.08,
    val explorationShare: Double = 0.20,
    val explorationMinShare: Double = 0.15,
    val explorationMaxShare: Double = 0.25,
    val candidateTimeoutMs: Long = 15_000L,
    val maxConcurrentRequests: Int = 4,
    val requestTimeoutMs: Long = 2_500L,
    val relatedSeedMin: Int = 3,
    val relatedSeedMax: Int = 5,
    val seedWindowDays: Int = 60,
    val seedHalfLifeDays: Double = 21.0,
    val previousSessionSeedPenalty: Double = 0.2,
    val previousSessionsToPenalize: Int = 3,
    val similarSeedMin: Int = 3,
    val similarSeedMax: Int = 4,
    val softmaxTemperature: Double = 0.7,
    val similarArtistLimit: Int = 30,
    val similarPickMin: Int = 6,
    val similarPickMax: Int = 8,
    val similarTracksMin: Int = 1,
    val similarTracksMax: Int = 2,
    val topTracksLimit: Int = 20,
    val topTracksPages: Int = 2,
    val secondHopArtistMin: Int = 1,
    val secondHopArtistMax: Int = 2,
    val secondHopResultMin: Int = 2,
    val secondHopResultMax: Int = 3,
    val tagSeedMin: Int = 1,
    val tagSeedMax: Int = 2,
    val tagPageMax: Int = 5,
    val followedFreshDays: Int = 60,
    val followedMaxPerBatch: Int = 2,
    val familiarMinUnplayedDays: Int = 21,
    val familiarMinUnshownDays: Int = 30,
    val impressionBlockDays: Int = 14,
    val playedBlockDays: Int = 7,
    val historyWindowDays: Int = 60,
    val penaltyWindowDays: Int = 3,
    val penaltyPerImpression: Double = 0.2,
    val maxArtistPenalty: Double = 0.6,
    val weightArtistAffinity: Double = 0.30,
    val weightGenreAffinity: Double = 0.15,
    val weightOriginPrior: Double = 0.20,
    val weightNovelty: Double = 0.20,
    val weightSeed: Double = 0.10,
    val weightPopularity: Double = 0.05,
    val newArtistBonus: Double = 0.3,
    val gumbelSigma: Double = 0.15,
    val mmrLambda: Double = 0.7,
    val artistWindow: Int = 5,
    val artistMaxPerTwenty: Int = 2,
    val seedRunMax: Int = 3,
    val autoBlockSkips: Int = 3,
    val autoBlockWindowDays: Int = 30,
    val autoBlockDurationDays: Int = 30,
    val minDurationMs: Long = 60_000L,
    val maxDurationMs: Long = 12 * 60_000L,
    val sessionKeep: Int = 10,
    val impressionRetentionDays: Int = 90,
    val cleanupIntervalHours: Int = 24,
)
