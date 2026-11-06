package com.facet.music.data.local

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.PlayedTrack
import com.facet.music.core.wave.WaveHistory
import com.facet.music.core.wave.WaveHistoryStore
import com.facet.music.core.wave.WaveImpression
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.core.wave.WaveSessionRecord
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.local.dao.LikedTracksDao
import com.facet.music.data.local.dao.PlayHistoryDao
import com.facet.music.data.local.dao.QueueDao
import com.facet.music.data.local.dao.TasteDao
import com.facet.music.data.local.dao.WaveDao
import com.facet.music.data.local.entity.ArtistBlocklistEntity
import com.facet.music.data.local.entity.PlayHistoryEntity
import com.facet.music.data.local.entity.UserPreferenceEntity
import com.facet.music.data.local.entity.WaveImpressionEntity
import com.facet.music.data.local.entity.WaveSessionEntity
import com.facet.music.data.local.entity.toTrack
import com.facet.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWaveHistoryStore @Inject constructor(
    private val waveDao: WaveDao,
    private val likedTracksDao: LikedTracksDao,
    private val playHistoryDao: PlayHistoryDao,
    private val dislikedTrackDao: DislikedTrackDao,
    private val queueDao: QueueDao,
    private val tasteDao: TasteDao,
    private val clock: Clock,
    private val json: Json,
) : WaveHistoryStore {

    override suspend fun loadHistory(nowMs: Long): WaveHistory = withContext(Dispatchers.IO) {
        val impressions = waveDao
            .impressionsSince(nowMs - IMPRESSION_WINDOW_DAYS * DAY_MS)
            .map { it.toDomain() }
        val plays = playHistoryDao.getAll(HISTORY_LIMIT).map { it.toPlayedTrack() }
        val liked = likedTracksDao.getAll().map { entity ->
            PlayedTrack(
                track = entity.toTrack(),
                atMs = entity.likedAt,
                playedFraction = null,
                liked = true,
            )
        }
        val familiar = (liked + plays)
            .sortedByDescending { it.atMs }
            .distinctBy { keyOf(it.track) }
        val disliked = dislikedTrackDao.getAll().mapTo(HashSet()) { it.trackId }
        val knownKeys = (liked + plays).associate {
            it.track.id to MusicNormalizer.trackKey(it.track.artist, it.track.title)
        }
        val dislikedKeys = disliked
            .mapNotNull { knownKeys[it] }
            .filter { it.isNotBlank() }
            .toSet()
        val blocked = waveDao.blocklist()
            .filter { it.until == null || it.until > nowMs }
            .mapTo(HashSet()) { it.artistKey }
        val queue = queueDao.getAll()
        WaveHistory(
            impressions = impressions,
            plays = plays,
            familiarTracks = familiar,
            dislikedTrackIds = disliked,
            dislikedTrackKeys = dislikedKeys,
            blockedArtistKeys = blocked,
            queueTrackIds = queue.mapTo(HashSet()) { it.trackId },
            queueTrackKeys = queue.mapTo(HashSet()) {
                MusicNormalizer.trackKey(it.artist, it.title)
            },
        )
    }

    override suspend fun seedTracks(sinceMs: Long): List<PlayedTrack> = withContext(Dispatchers.IO) {
        val likes = likedTracksDao.getAll()
            .filter { it.likedAt >= sinceMs }
            .map { entity ->
                PlayedTrack(
                    track = entity.toTrack(),
                    atMs = entity.likedAt,
                    playedFraction = null,
                    liked = true,
                )
            }
        val finished = playHistoryDao.getAll(HISTORY_LIMIT)
            .filter {
                it.playedAt >= sinceMs &&
                    ((it.playedFraction ?: LEGACY_FINISHED_FRACTION) > FINISHED_THRESHOLD)
            }
            .map { it.toPlayedTrack() }
        (likes + finished)
            .sortedByDescending { it.atMs }
            .distinctBy { keyOf(it.track) }
    }

    override suspend fun previousSessionSeedKeys(limit: Int): Set<String> =
        withContext(Dispatchers.IO) {
            waveDao.recentSeedTrackKeys(limit)
                .flatMap { raw ->
                    runCatching { json.decodeFromString<List<String>>(raw) }
                        .getOrDefault(emptyList())
                }
                .toSet()
        }

    override suspend fun previousSessionSeedArtistKeys(limit: Int): Set<String> =
        withContext(Dispatchers.IO) {
            waveDao.sessions(limit)
                .flatMap { entity ->
                    runCatching {
                        json.decodeFromString<List<String>>(entity.seedArtistKeys)
                    }.getOrDefault(emptyList())
                }
                .toSet()
        }

    override suspend fun recordShown(sessionId: String, candidates: List<Candidate>) =
        withContext(Dispatchers.IO) {
            val now = clock.nowMs()
            candidates.forEach { candidate ->
                val track = candidate.track
                waveDao.insertImpression(
                    WaveImpressionEntity(
                        trackKey = keyOf(track),
                        trackId = track.id,
                        artistKey = artistKeyOf(track),
                        sessionId = sessionId,
                        origin = candidate.origin.name,
                        seedKey = candidate.seedKey,
                        shownAt = now,
                        outcome = WaveOutcome.SHOWN.name,
                        playedMs = 0L,
                    ),
                )
            }
        }

    override suspend fun recordOutcome(
        sessionId: String?,
        track: Track,
        outcome: WaveOutcome,
        playedMs: Long,
    ) = withContext(Dispatchers.IO) {
        val key = keyOf(track)
        val updated = if (sessionId != null) {
            waveDao.updateOutcome(sessionId, key, outcome.name, playedMs)
        } else {
            0
        }
        if (updated == 0) {
            waveDao.updateLatestOutcome(key, outcome.name, playedMs)
        }

        if (outcome == WaveOutcome.SKIPPED && playedMs < FAST_SKIP_MS) {
            val now = clock.nowMs()
            val artistKey = artistKeyOf(track)
            val skips = waveDao.countFastSkips(
                artistKey = artistKey,
                since = now - AUTO_BLOCK_WINDOW_DAYS * DAY_MS,
                thresholdMs = FAST_SKIP_MS,
            )
            if (skips >= AUTO_BLOCK_SKIPS) {
                waveDao.upsertBlock(
                    ArtistBlocklistEntity(
                        artistKey = artistKey,
                        createdAt = now,
                        until = now + AUTO_BLOCK_DURATION_DAYS * DAY_MS,
                        reason = REASON_AUTO_SKIPS,
                    ),
                )
            }
        }
    }

    override suspend fun saveSession(record: WaveSessionRecord) = withContext(Dispatchers.IO) {
        waveDao.deleteExpiredBlocks(clock.nowMs())
        waveDao.upsertSession(
            WaveSessionEntity(
                sessionId = record.sessionId,
                createdAt = record.createdAt,
                randomSeed = record.randomSeed,
                mode = record.mode,
                seedArtistKeys = json.encodeToString(record.seedArtistKeys),
                seedTrackKeys = json.encodeToString(record.seedTrackKeys),
                isRadio = record.isRadio,
                radioSeed = record.radioSeed,
                cursor = record.cursor,
            ),
        )
        waveDao.trimSessions(SESSION_KEEP)
    }

    override suspend fun cleanupIfNeeded(nowMs: Long) = withContext(Dispatchers.IO) {
        val last = tasteDao.getPreference(PREF_CLEANUP)?.toLongOrNull() ?: 0L
        if (nowMs - last < CLEANUP_INTERVAL_MS) return@withContext
        val cutoff = nowMs - IMPRESSION_RETENTION_DAYS * DAY_MS
        waveDao.deleteImpressionsBefore(cutoff)
        tasteDao.upsertPreference(
            UserPreferenceEntity(
                preferenceKey = PREF_CLEANUP,
                preferenceValue = nowMs.toString(),
            ),
        )
    }

    override suspend fun blockArtist(artistKey: String, nowMs: Long) =
        withContext(Dispatchers.IO) {
            waveDao.upsertBlock(
                ArtistBlocklistEntity(
                    artistKey = artistKey,
                    createdAt = nowMs,
                    until = null,
                    reason = REASON_USER,
                ),
            )
        }

    private fun PlayHistoryEntity.toPlayedTrack(): PlayedTrack = PlayedTrack(
        track = com.facet.music.data.model.Track(
            id = id,
            source = runCatching { com.facet.music.data.model.Source.valueOf(source) }
                .getOrDefault(com.facet.music.data.model.Source.SOUNDCLOUD),
            title = title,
            artist = artist,
            artistUrl = artistUrl,
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = durationMs,
            genre = genre,
            transcodingUrl = transcodingUrl,
        ),
        atMs = playedAt,
        playedFraction = playedFraction,
        liked = false,
    )

    private fun WaveImpressionEntity.toDomain(): WaveImpression = WaveImpression(
        trackKey = trackKey,
        trackId = trackId,
        artistKey = artistKey,
        sessionId = sessionId,
        origin = runCatching {
            CandidateOrigin.valueOf(origin)
        }.getOrDefault(CandidateOrigin.RELATED),
        seedKey = seedKey,
        shownAt = shownAt,
        outcome = runCatching { WaveOutcome.valueOf(outcome) }
            .getOrDefault(WaveOutcome.SHOWN),
        playedMs = playedMs,
    )

    private fun keyOf(track: Track): String =
        MusicNormalizer.trackKey(track.artist, track.title).ifBlank { "id:${track.id}" }

    private fun artistKeyOf(track: Track): String =
        MusicNormalizer.normalizeArtist(track.artist).ifBlank { "track:${track.id}" }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HISTORY_LIMIT = 50
        const val IMPRESSION_WINDOW_DAYS = 30
        const val IMPRESSION_RETENTION_DAYS = 90
        const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val FINISHED_THRESHOLD = 0.8f
        const val LEGACY_FINISHED_FRACTION = 1f
        const val FAST_SKIP_MS = 30_000L
        const val AUTO_BLOCK_SKIPS = 3
        const val AUTO_BLOCK_WINDOW_DAYS = 30
        const val AUTO_BLOCK_DURATION_DAYS = 30
        const val SESSION_KEEP = 10
        const val PREF_CLEANUP = "wave_cleanup_at"
        const val REASON_USER = "USER"
        const val REASON_AUTO_SKIPS = "AUTO_SKIPS"
    }
}
