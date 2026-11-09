package com.facet.music.data.source.wave

import android.util.Log
import com.facet.music.core.analytics.AnalyticsEvents
import com.facet.music.core.analytics.AnalyticsTracker
import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.SeedArtist
import com.facet.music.core.wave.TasteProfileProvider
import com.facet.music.core.wave.TrackSeed
import com.facet.music.core.wave.WaveEngine
import com.facet.music.core.wave.WaveGeneration
import com.facet.music.core.wave.WaveHistoryStore
import com.facet.music.core.wave.WaveMood
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.core.wave.WaveSessionRecord
import com.facet.music.core.wave.WaveSessionState
import com.facet.music.data.model.Track
import com.facet.music.data.repository.ArtistRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredSeedTrack(
    val track: Track,
    val weight: Double,
    val trackKey: String,
    val artistKey: String,
)

@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val engine: WaveEngine,
    private val historyStore: WaveHistoryStore,
    private val tasteProfileProvider: TasteProfileProvider,
    private val clock: Clock,
    private val json: Json,
    private val analyticsTracker: AnalyticsTracker,
    private val artistRepository: ArtistRepository,
) : WaveRepository {

    private val _activeSession = MutableStateFlow<WaveSession?>(null)
    override val activeSession: StateFlow<WaveSession?> = _activeSession.asStateFlow()

    private val mutex = Mutex()
    private var plan: WaveSessionPlan? = null
    private var state = WaveSessionState()
    private var details = emptyMap<String, com.facet.music.core.wave.WaveCandidateDetails>()
    private var recordedShownKeys = emptySet<String>()
    private var fastSkipSeed: String? = null
    private var fastSkipSeedCount = 0
    private var consecutiveFastSkips = 0
    private var moodRefreshPending = false
    private var statBatches = 0
    private var statShown = 0
    private var statSkipped = 0
    private var statPlayed = 0
    private var statLiked = 0
    private var statFamiliar = 0
    private var statNew = 0
    private var statShort = 0

    override suspend fun startSession(
        seeds: List<String>,
        mood: WaveMood,
        seedArtistNames: List<String>,
    ): Result<WaveSession> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val excluded = state.shownTrackKeys
                var fresh = engine.startSession(mood = mood, seedArtistNames = seedArtistNames)
                if (seedArtistNames.isNotEmpty() && fresh.seedTracks.isEmpty()) {
                    fresh = fresh.copy(seedTracks = seedTracksForArtists(seedArtistNames))
                }
                fresh = fresh.copy(excludedTrackKeys = excluded)
                applyPlan(fresh, seeds)
                fresh.toUiSession(seeds).also { _activeSession.value = it }
            }
        }.rethrowCancellation()
    }

    override suspend fun startRadio(seed: Track): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val excluded = state.shownTrackKeys
                    val fresh = engine.startSession(isRadio = true, radioSeed = seed)
                        .copy(excludedTrackKeys = excluded)
                    applyPlan(fresh, listOf(seed.id))
                    fresh.toUiSession(listOf(seed.id)).copy(radioMode = true).also {
                        _activeSession.value = it
                    }
                }
            }.rethrowCancellation()
        }

    override suspend fun startRadioForArtist(artist: String): Result<WaveSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val key = MusicNormalizer.normalizeArtist(artist)
                    val excluded = state.shownTrackKeys
                    val found = runCatching { artistRepository.resolveByName(artist).getOrNull() }
                        .getOrNull()
                    val popular = found?.let {
                        runCatching {
                            artistRepository.popularTracks(it.source, it.url, RADIO_ARTIST_TRACKS)
                                .getOrNull()
                                .orEmpty()
                        }.getOrNull().orEmpty()
                    }.orEmpty()
                    val radioSeeds = popular.map {
                        TrackSeed(it, 1.0, keyOf(it), artistKeyOf(it))
                    }
                    val fresh = engine.startSession(isRadio = true, seedArtistNames = listOf(artist))
                        .copy(
                            seedTracks = radioSeeds,
                            radioArtistKey = key,
                            excludedTrackKeys = excluded,
                        )
                    applyPlan(fresh, emptyList())
                    _activeSession.value = fresh.toUiSession(emptyList()).copy(radioMode = true)
                    fresh.toUiSession(emptyList()).copy(radioMode = true)
                }
            }.rethrowCancellation()
        }

    override suspend fun nextBatch(size: Int): Result<WaveBatch> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    if (plan == null) restoreLocked()
                    val currentPlan = plan ?: error("Сессия волны не запущена")
                    val active = _activeSession.value
                    if (active?.isTerminated == true) {
                        return@withLock WaveBatch(emptyList(), active)
                    }
                    val generation = engine.nextBatch(currentPlan, state, size)
                    state = generation.state
                    details = details + generation.details
                    val tracks = generation.tracks.filterNot {
                        it.id in active?.shownTrackIds.orEmpty()
                    }
                    if (tracks.isEmpty() && generation.tracks.isEmpty()) {
                        Log.w(
                            TAG,
                            "Пустая пачка: artists=${currentPlan.seedArtists.size}, " +
                                "genres=${currentPlan.seedGenres.size}, " +
                                "tracks=${currentPlan.seedTracks.size}, " +
                                "origins=${generation.origins}",
                        )
                        val terminated = active?.copy(
                            isTerminated = true,
                            batchNumber = state.batchNumber,
                        ) ?: currentPlan.toUiSession(emptyList())
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
                    recordBatchStats(generation, tracks.size)
                    persistSession(currentPlan, state)
                    WaveBatch(tracks, updated, generation.details)
                }
            }.rethrowCancellation()
        }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        emitWaveStats()
        _activeSession.value = null
        mutex.withLock {
            _activeSession.value = null
            plan = null
            state = WaveSessionState()
            details = emptyMap()
            recordedShownKeys = emptySet()
            fastSkipSeed = null
            fastSkipSeedCount = 0
            consecutiveFastSkips = 0
            moodRefreshPending = false
        }
    }

    override suspend fun recordOutcome(track: Track, outcome: WaveOutcome, playedMs: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val currentPlan = plan ?: restoreLocked().let { plan } ?: return@withLock
                historyStore.recordOutcome(
                    sessionId = _activeSession.value?.sessionId,
                    track = track,
                    outcome = outcome,
                    playedMs = playedMs,
                )
                val key = keyOf(track)
                val impression = historyStore.impressionFor(key)
                when (outcome) {
                    WaveOutcome.FINISHED -> {
                        adjustSeed(impression?.seedKey, 1.3)
                        resetFastSkips()
                    }

                    WaveOutcome.SKIPPED -> if (playedMs < FAST_SKIP_MS) {
                        statSkipped++
                        adjustSeed(impression?.seedKey, 0.5)
                        registerFastSkip(impression?.seedKey, currentPlan)
                    }

                    WaveOutcome.PLAYED -> {
                        statPlayed++
                        resetFastSkips()
                    }
                    else -> Unit
                }
                persistSession(plan ?: currentPlan, state)
            }
        }

    override suspend fun recordShown(track: Track) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentPlan = plan ?: return@withLock
            val session = _activeSession.value ?: return@withLock
            val key = keyOf(track)
            if (key in recordedShownKeys) return@withLock
            historyStore.recordShown(
                session.sessionId,
                listOf(
                    Candidate(
                        track = track,
                        origin = details[key]?.origin ?: CandidateOrigin.RELATED,
                        seedKey = details[key]?.seedKey,
                    ),
                ),
            )
            recordedShownKeys = recordedShownKeys + key
            state = state.copy(
                shownTrackKeys = state.shownTrackKeys + key,
                recentArtistKeys = (state.recentArtistKeys + artistKeyOf(track))
                    .takeLast(RECENT_KEYS_KEPT),
            )
            _activeSession.value = session.copy(
                shownTrackIds = session.shownTrackIds + track.id,
            )
            persistSession(currentPlan, state)
        }
    }

    override suspend fun onTrackLiked(track: Track) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentPlan = plan ?: restoreLocked().let { plan } ?: return@withLock
            val sessionId = _activeSession.value?.sessionId
            statLiked++
            historyStore.recordOutcome(sessionId, track, WaveOutcome.LIKED, 0L)
            val key = keyOf(track)
            val seedKey = historyStore.impressionFor(key)?.seedKey
            adjustSeed(seedKey, 2.0)
            if (currentPlan.seedTracks.none { it.trackKey == key }) {
                plan = (plan ?: currentPlan).copy(
                    seedTracks = (plan ?: currentPlan).seedTracks + TrackSeed(
                        track = track,
                        weight = 2.0,
                        trackKey = key,
                        artistKey = artistKeyOf(track),
                    ),
                )
            }
            resetFastSkips()
            persistSession(plan ?: currentPlan, state)
        }
    }

    override suspend fun onTrackDisliked(track: Track) = withContext(Dispatchers.IO) {
        mutex.withLock {
            historyStore.recordOutcome(
                _activeSession.value?.sessionId,
                track,
                WaveOutcome.DISLIKED,
                0L,
            )
            historyStore.blockArtist(artistKeyOf(track), clock.nowMs())
            resetFastSkips()
        }
    }

    override suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { restoreLocked() }
    }

    override suspend fun consumeMoodRefresh(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = moodRefreshPending
            moodRefreshPending = false
            pending
        }
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
        if (batch.tracks.isEmpty()) Log.w(TAG, "Волна не собралась: ${sessionDebug()}")
        WaveSelection(
            tracks = batch.tracks,
            fromLikes = batch.session.fromLikes,
            fromHistory = batch.session.fromHistory,
            fromGenres = batch.session.fromGenres,
            fromSimilar = batch.session.fromSimilar,
        )
    }.rethrowCancellation()

    private suspend fun seedTracksForArtists(names: List<String>): List<TrackSeed> =
        names.take(COLD_START_SEED_ARTISTS).flatMap { name ->
            val artist = runCatching { artistRepository.resolveByName(name).getOrNull() }
                .getOrNull() ?: return@flatMap emptyList()
            val tracks = runCatching {
                artistRepository.popularTracks(artist.source, artist.url, COLD_START_TRACKS)
                    .getOrNull()
                    .orEmpty()
            }.getOrDefault(emptyList())
            tracks.map { TrackSeed(it, 1.0, keyOf(it), artistKeyOf(it)) }
        }.distinctBy { it.trackKey }

    private suspend fun applyPlan(fresh: WaveSessionPlan, seeds: List<String>) {
        plan = fresh
        state = WaveSessionState()
        details = emptyMap()
        recordedShownKeys = emptySet()
        fastSkipSeed = null
        fastSkipSeedCount = 0
        consecutiveFastSkips = 0
        moodRefreshPending = false
        statBatches = 0
        statShown = 0
        statSkipped = 0
        statPlayed = 0
        statLiked = 0
        statFamiliar = 0
        statNew = 0
        statShort = 0
        if (fresh.seedArtists.isEmpty() && fresh.seedTracks.isEmpty()) {
            Log.w(TAG, "Сессия без семян: нет вкусов и истории")
        } else {
            Log.w(
                TAG,
                "Сессия: artists=${fresh.seedArtists.size}, genres=${fresh.seedGenres.size}, " +
                    "tracks=${fresh.seedTracks.size}",
            )
        }
        persistSession(fresh, state)
        _activeSession.value = fresh.toUiSession(seeds)
    }

    private suspend fun restoreLocked(): Boolean {
        if (plan != null) return true
        val record = historyStore.latestSession() ?: return false
        val now = clock.nowMs()
        val history = historyStore.loadHistory(now)
        val profile = tasteProfileProvider.profile()
        val seedTracks = record.seedTracksJson
            ?.let { runCatching { json.decodeFromString<List<StoredSeedTrack>>(it) }.getOrNull() }
            .orEmpty()
            .map { TrackSeed(it.track, it.weight, it.trackKey, it.artistKey) }
        val seedArtists = record.seedArtistKeys.map { key ->
            SeedArtist(key, key, 1.0)
        }
        if (seedArtists.isEmpty() && seedTracks.isEmpty()) return false
        val restored = WaveSessionPlan(
            sessionId = record.sessionId,
            createdAt = record.createdAt,
            randomSeed = record.randomSeed,
            profile = profile,
            history = history,
            seedArtists = seedArtists,
            seedGenres = seedTracks.mapNotNull { it.track.genre }.distinct(),
            seedTracks = seedTracks,
            previousSeedKeys = emptySet(),
            previousSeedArtistKeys = emptySet(),
            isRadio = record.isRadio,
            radioSeed = if (record.isRadio) seedTracks.firstOrNull()?.track else null,
            radioArtistKey = record.radioArtistKey,
            mood = WaveMood.fromName(record.mood),
        )
        val sessionImpressions = history.impressions.filter { it.sessionId == record.sessionId }
        plan = restored
        recordedShownKeys = sessionImpressions.mapTo(mutableSetOf()) { it.trackKey }
        state = WaveSessionState(
            batchNumber = record.cursor,
            shownTrackKeys = sessionImpressions.mapTo(mutableSetOf()) { it.trackKey },
            recentArtistKeys = sessionImpressions.map { it.artistKey }.takeLast(RECENT_KEYS_KEPT),
            recentSeedKeys = sessionImpressions.mapNotNull { it.seedKey }.takeLast(RECENT_KEYS_KEPT),
        )
        _activeSession.value = restored.toUiSession(emptyList()).copy(
            batchNumber = record.cursor,
            isTerminated = false,
        )
        Log.w(TAG, "Сессия восстановлена: ${record.sessionId}, cursor=${record.cursor}")
        return true
    }

    private fun recordBatchStats(generation: WaveGeneration, shown: Int) {
        statBatches++
        statShown += shown
        statFamiliar += generation.origins[CandidateOrigin.FAMILIAR] ?: 0
        statNew += generation.details.values.count { it.novelty >= 1.0 }
        if (generation.isShort) statShort++
        if (statBatches % STATS_EMIT_BATCHES == 0) emitWaveStats()
    }

    private fun emitWaveStats() {
        if (statBatches == 0 || statShown == 0) return
        val outcomes = (statSkipped + statPlayed).coerceAtLeast(1)
        val event = buildString {
            append(AnalyticsEvents.WAVE_SUMMARY)
            append(":len=").append(statShown)
            append(";batches=").append(statBatches)
            append(";skip=").append(statSkipped * 100 / outcomes)
            append(";like=").append(statLiked * 100 / statShown)
            append(";new=").append(statNew * 100 / statShown)
            append(";family=").append(statFamiliar * 100 / statShown)
            append(";short=").append(statShort * 100 / statBatches)
        }
        analyticsTracker.track(event)
        statBatches = 0
        statShown = 0
        statSkipped = 0
        statPlayed = 0
        statLiked = 0
        statFamiliar = 0
        statNew = 0
        statShort = 0
    }

    private fun adjustSeed(seedKey: String?, factor: Double) {
        if (seedKey.isNullOrBlank()) return
        val current = plan ?: return
        plan = current.copy(
            seedTracks = current.seedTracks.map { seed ->
                if (seed.trackKey == seedKey || seed.artistKey == seedKey) {
                    seed.copy(weight = (seed.weight * factor).coerceIn(0.05, 8.0))
                } else {
                    seed
                }
            },
        )
    }

    private fun registerFastSkip(seedKey: String?, currentPlan: WaveSessionPlan) {
        consecutiveFastSkips++
        if (consecutiveFastSkips >= MOOD_REFRESH_SKIPS) moodRefreshPending = true
        if (seedKey.isNullOrBlank()) return
        if (fastSkipSeed == seedKey) {
            fastSkipSeedCount++
        } else {
            fastSkipSeed = seedKey
            fastSkipSeedCount = 1
        }
        if (fastSkipSeedCount >= DISABLE_SEED_SKIPS) {
            val effective = plan ?: currentPlan
            plan = effective.copy(seedTracks = effective.seedTracks.filterNot {
                it.trackKey == seedKey || it.artistKey == seedKey
            })
        }
    }

    private fun resetFastSkips() {
        fastSkipSeed = null
        fastSkipSeedCount = 0
        consecutiveFastSkips = 0
    }

    private suspend fun persistSession(current: WaveSessionPlan, currentState: WaveSessionState) {
        historyStore.saveSession(
            WaveSessionRecord(
                sessionId = current.sessionId,
                createdAt = current.createdAt,
                randomSeed = current.randomSeed,
                mode = if (current.isRadio) MODE_RADIO else MODE_NORMAL,
                seedArtistKeys = current.seedArtists.map { it.key },
                seedTrackKeys = current.seedTracks.map { it.trackKey },
                isRadio = current.isRadio,
                radioSeed = current.radioSeed?.id,
                cursor = currentState.batchNumber,
                mood = current.mood.name,
                radioArtistKey = current.radioArtistKey,
                seedTracksJson = json.encodeToString(
                    current.seedTracks.map {
                        StoredSeedTrack(it.track, it.weight, it.trackKey, it.artistKey)
                    },
                ),
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

    private fun keyOf(track: Track): String =
        MusicNormalizer.trackKey(track.artist, track.title).ifBlank { "id:${track.id}" }

    private fun artistKeyOf(track: Track): String =
        MusicNormalizer.normalizeArtist(track.artist).ifBlank { "track:${track.id}" }

    private companion object {
        const val TAG = "WaveRepository"
        const val MODE_NORMAL = "NORMAL"
        const val MODE_RADIO = "RADIO"
        const val FAST_SKIP_MS = 30_000L
        const val MOOD_REFRESH_SKIPS = 3
        const val DISABLE_SEED_SKIPS = 2
        const val STATS_EMIT_BATCHES = 5
        const val RADIO_ARTIST_TRACKS = 5
        const val COLD_START_SEED_ARTISTS = 3
        const val COLD_START_TRACKS = 3
        const val RECENT_KEYS_KEPT = 40
    }
}
