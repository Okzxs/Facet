package com.facet.music.data.source.wave

import android.util.Log
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.WaveEngine
import com.facet.music.core.wave.WaveGeneration
import com.facet.music.core.wave.WaveHistoryStore
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.core.wave.WaveSessionRecord
import com.facet.music.core.wave.WaveSessionState
import com.facet.music.data.model.Track
import com.facet.music.data.repository.WaveBatch
import com.facet.music.data.repository.WaveRepository
import com.facet.music.data.repository.WaveSelection
import com.facet.music.data.repository.WaveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тонкая обёртка над [WaveEngine]: хранит активную сессию, счётчики для UI
 * и пишет сессию/показы в Room. Вся логика отбора — в ядре.
 */
@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val engine: WaveEngine,
    private val historyStore: WaveHistoryStore,
    private val clock: Clock,
) : WaveRepository {

    private val _activeSession = MutableStateFlow<WaveSession?>(null)
    override val activeSession: StateFlow<WaveSession?> = _activeSession.asStateFlow()

    private val mutex = Mutex()
    private var plan: WaveSessionPlan? = null
    private var state = WaveSessionState()

    override suspend fun startSession(seeds: List<String>): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val fresh = engine.startSession()
                    plan = fresh
                    state = WaveSessionState()
                    if (fresh.seedArtists.isEmpty() && fresh.seedTracks.isEmpty()) {
                        Log.w(TAG, "Сессия без семян: нет вкусов и истории")
                    } else {
                        Log.w(
                            TAG,
                            "Сессия: artists=${fresh.seedArtists.size}, " +
                                "genres=${fresh.seedGenres.size}, tracks=${fresh.seedTracks.size}",
                        )
                    }
                    val session = fresh.toUiSession(seeds)
                    persistSession(fresh, state)
                    _activeSession.value = session
                    session
                }
            }.rethrowCancellation()
        }

    override suspend fun startRadio(seed: Track): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val fresh = engine.startSession(isRadio = true, radioSeed = seed)
                    plan = fresh
                    state = WaveSessionState()
                    val session = fresh.toUiSession(listOf(seed.id)).copy(radioMode = true)
                    persistSession(fresh, state)
                    _activeSession.value = session
                    session
                }
            }.rethrowCancellation()
        }

    override suspend fun nextBatch(size: Int): Result<WaveBatch> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val currentPlan = plan ?: error("Сессия волны не запущена")
                    val active = _activeSession.value
                    if (active?.isTerminated == true) {
                        return@withLock WaveBatch(emptyList(), active)
                    }
                    val generation = engine.nextBatch(currentPlan, state, size)
                    state = generation.state
                    val tracks = generation.tracks.filterNot { it.id in active?.shownTrackIds.orEmpty() }
                    if (tracks.isEmpty() && generation.tracks.isEmpty()) {
                        Log.w(
                            TAG,
                            "Пустая пачка: artists=${currentPlan.seedArtists.size}, " +
                                "genres=${currentPlan.seedGenres.size}, " +
                                "tracks=${currentPlan.seedTracks.size}, " +
                                "origins=${generation.origins}",
                        )
                        val terminated = active?.copy(isTerminated = true, batchNumber = state.batchNumber)
                            ?: currentPlan.toUiSession(emptyList())
                        _activeSession.value = terminated
                        persistSession(currentPlan, state)
                        return@withLock WaveBatch(emptyList(), terminated)
                    }
                    if (generation.isShort) {
                        Log.w(
                            TAG,
                            "Короткая пачка волны: ${tracks.size}/${generation.requestedSize}, " +
                                "origins=${generation.origins}",
                        )
                    }
                    val updated = active
                        ?.copy(
                            shownTrackIds = active.shownTrackIds + tracks.map { it.id },
                            batchNumber = state.batchNumber,
                            isTerminated = false,
                            fromLikes = active.fromLikes + count(generation, CandidateOrigin.FAMILIAR),
                            fromHistory = active.fromHistory,
                            fromGenres = active.fromGenres + count(generation, CandidateOrigin.TAG),
                            fromSimilar = active.fromSimilar + count(
                                generation,
                                CandidateOrigin.RELATED,
                                CandidateOrigin.SIMILAR_ARTIST,
                                CandidateOrigin.SECOND_HOP,
                                CandidateOrigin.FOLLOWED,
                            ),
                        )
                        ?: currentPlan.toUiSession(tracks.map { it.id })
                    _activeSession.value = updated
                    persistSession(currentPlan, state)
                    WaveBatch(tracks, updated)
                }
            }.rethrowCancellation()
        }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        _activeSession.value = null
        mutex.withLock {
            _activeSession.value = null
            plan = null
            state = WaveSessionState()
        }
    }

    override suspend fun recordOutcome(track: Track, outcome: WaveOutcome, playedMs: Long) =
        withContext(Dispatchers.IO) {
            historyStore.recordOutcome(
                sessionId = _activeSession.value?.sessionId,
                track = track,
                outcome = outcome,
                playedMs = playedMs,
            )
        }

    override suspend fun cleanupIfNeeded() = withContext(Dispatchers.IO) {
        historyStore.cleanupIfNeeded(clock.nowMs())
    }

    override suspend fun blockArtist(artistKey: String) = withContext(Dispatchers.IO) {
        historyStore.blockArtist(artistKey, clock.nowMs())
    }

    override suspend fun generateWave(): Result<WaveSelection> = runCatching {
        startSession().getOrThrow()
        val batch = nextBatch(configSize()).getOrThrow()
        if (batch.tracks.isEmpty()) {
            Log.w(TAG, "Волна не собралась: ${sessionDebug()}")
        }
        WaveSelection(
            tracks = batch.tracks,
            fromLikes = batch.session.fromLikes,
            fromHistory = batch.session.fromHistory,
            fromGenres = batch.session.fromGenres,
            fromSimilar = batch.session.fromSimilar,
        )
    }.rethrowCancellation()

    private suspend fun persistSession(plan: WaveSessionPlan, state: WaveSessionState) {
        historyStore.saveSession(
            WaveSessionRecord(
                sessionId = plan.sessionId,
                createdAt = plan.createdAt,
                randomSeed = plan.randomSeed,
                mode = if (plan.isRadio) MODE_RADIO else MODE_NORMAL,
                seedArtistKeys = plan.seedArtists.map { it.key },
                seedTrackKeys = plan.seedTracks.map { it.trackKey },
                isRadio = plan.isRadio,
                radioSeed = plan.radioSeed?.id,
                cursor = state.batchNumber,
            ),
        )
    }

    private fun WaveSessionPlan.toUiSession(seedIds: List<String>): WaveSession = WaveSession(
        sessionId = sessionId,
        seedTrackIds = seedTracks.map { it.track.id },
        seedArtists = seedArtists.map { it.displayName },
        seedGenres = seedGenres,
        shownTrackIds = seedIds.toSet(),
        batchNumber = 0,
        isRussianPriority = false,
        isTerminated = false,
        radioMode = isRadio,
    )

    private fun count(generation: WaveGeneration, vararg origins: CandidateOrigin): Int =
        origins.sumOf { generation.origins[it] ?: 0 }

    private fun configSize(): Int = WaveRepository.DEFAULT_BATCH_SIZE

    private fun sessionDebug(): String {
        val current = plan
        return if (current == null) {
            "сессия не создана"
        } else {
            "artists=${current.seedArtists.size}, genres=${current.seedGenres.size}, " +
                "tracks=${current.seedTracks.size}"
        }
    }

    private companion object {
        const val TAG = "WaveRepository"
        const val MODE_NORMAL = "NORMAL"
        const val MODE_RADIO = "RADIO"
    }
}
