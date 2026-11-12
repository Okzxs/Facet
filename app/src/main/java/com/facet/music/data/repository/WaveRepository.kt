package com.facet.music.data.repository

import com.facet.music.core.wave.WaveCandidateDetails
import com.facet.music.core.wave.WaveMood
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.StateFlow

data class WaveSelection(
    val tracks: List<Track>,
    val fromLikes: Int,
    val fromHistory: Int,
    val fromGenres: Int,
    val fromSimilar: Int = 0,
)

data class WaveSession(
    val sessionId: String,
    val seedTrackIds: List<String> = emptyList(),
    val seedArtists: List<String> = emptyList(),
    val seedGenres: List<String> = emptyList(),
    val shownTrackIds: Set<String> = emptySet(),
    val batchNumber: Int = 0,
    val isTerminated: Boolean = false,
    val radioMode: Boolean = false,
    val fromLikes: Int = 0,
    val fromHistory: Int = 0,
    val fromGenres: Int = 0,
    val fromSimilar: Int = 0,
)

data class WaveBatch(
    val tracks: List<Track>,
    val session: WaveSession,
    val details: Map<String, WaveCandidateDetails> = emptyMap(),
)

interface WaveRepository {

    val activeSession: StateFlow<WaveSession?>

    suspend fun startSession(
        seeds: List<String> = emptyList(),
        mood: WaveMood = WaveMood.BALANCE,
        seedArtistNames: List<String> = emptyList(),
    ): Result<WaveSession>

    suspend fun startRadio(seed: Track): Result<WaveSession>

    suspend fun startRadioForArtist(artist: String): Result<WaveSession>

    suspend fun nextBatch(size: Int = DEFAULT_BATCH_SIZE): Result<WaveBatch>

    suspend fun reset()

    suspend fun recordOutcome(track: Track, outcome: WaveOutcome, playedMs: Long)

    suspend fun recordShown(track: Track)

    suspend fun onTrackLiked(track: Track)

    suspend fun onTrackDisliked(track: Track)

    suspend fun restoreSession(): Boolean

    suspend fun consumeMoodRefresh(): Boolean

    suspend fun cleanupIfNeeded()

    suspend fun blockArtist(artistKey: String)

    suspend fun generateWave(): Result<WaveSelection>

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}
