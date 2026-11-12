package com.facet.music.core.wave

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

interface CandidateSource {
    suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate>
}

class WaveNetworkGate(maxConcurrent: Int = 4) {

    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

    suspend fun <T> withPermit(block: suspend () -> T): T =
        semaphore.withPermit { block() }
}

interface TasteProfileProvider {
    suspend fun profile(): WaveTasteProfile
}

interface WaveHistoryStore {

    suspend fun loadHistory(nowMs: Long): WaveHistory

    suspend fun seedTracks(sinceMs: Long): List<PlayedTrack>

    suspend fun previousSessionSeedKeys(limit: Int): Set<String>

    suspend fun previousSessionSeedArtistKeys(limit: Int): Set<String>

    suspend fun recordShown(sessionId: String, candidates: List<Candidate>)

    suspend fun impressionFor(trackKey: String): WaveImpression?

    suspend fun latestSession(): WaveSessionRecord?

    suspend fun recordOutcome(
        sessionId: String?,
        track: com.facet.music.data.model.Track,
        outcome: WaveOutcome,
        playedMs: Long,
    )

    suspend fun saveSession(record: WaveSessionRecord)

    suspend fun cleanupIfNeeded(nowMs: Long)

    suspend fun blockArtist(artistKey: String, nowMs: Long)
}
