package com.facet.music.data.local

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.data.local.dao.CanonicalTrackDao
import com.facet.music.data.local.entity.CanonicalTrackEntity
import com.facet.music.data.local.entity.TrackVariantEntity
import com.facet.music.data.model.CanonicalTrack
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackVariant
import com.facet.music.data.repository.CanonicalTrackRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CanonicalTrackRepositoryImpl @Inject constructor(
    private val dao: CanonicalTrackDao,
) : CanonicalTrackRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun canonicalKey(track: Track): String {
        val key = MusicNormalizer.trackKey(track.artist, track.title)
        return key.ifBlank { "id:${track.id}" }
    }

    override suspend fun canonicalize(tracks: List<Track>): List<CanonicalTrack> {
        val groups = LinkedHashMap<String, MutableList<Track>>()
        tracks.forEach { track ->
            if (track.title.isBlank()) return@forEach
            val bucket = groups.getOrPut(canonicalKey(track)) { mutableListOf() }
            if (bucket.none { it.id == track.id }) bucket += track
        }
        return groups.map { (key, members) ->
            val ordered = members.sortedWith(playbackPreference)
            val primary = ordered.first()
            CanonicalTrack(
                key = key,
                title = primary.title,
                artist = primary.artist,
                primary = primary,
                variants = ordered.map { track ->
                    TrackVariant(
                        track = track,
                        confidence = confidence(primary, track),
                        lastCheckedAt = 0L,
                    )
                },
            )
        }
    }

    override suspend fun variants(canonicalKey: String): List<Track> =
        dao.variants(canonicalKey)
            .mapNotNull(::decode)
            .sortedWith(playbackPreference)

    override suspend fun bestForPlayback(canonicalKey: String): Track? =
        variants(canonicalKey).firstOrNull()

    override suspend fun fallbackFor(track: Track): Track? =
        dao.variants(canonicalKey(track))
            .mapNotNull(::decode)
            .filter { it.id != track.id }
            .filter { it.streamUrl != null || it.transcodingUrl != null }
            .sortedWith(playbackPreference)
            .firstOrNull()

    override suspend fun recordVariant(track: Track, confidence: Double) {
        if (track.title.isBlank() || track.id.isBlank()) return
        val key = canonicalKey(track)
        val now = System.currentTimeMillis()
        dao.upsertVariant(track.toEntity(key, confidence, now))
        refreshCanonical(key, now)
        dao.deleteVariantsOlderThan(now - VARIANT_TTL_MS)
        dao.deleteCanonicalOlderThan(now - CANONICAL_TTL_MS)
    }

    override suspend fun recordVariants(tracks: List<Track>, confidence: Double) {
        val candidates = tracks.filter { it.title.isNotBlank() && it.id.isNotBlank() }
        if (candidates.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = candidates.map { it.toEntity(canonicalKey(it), confidence, now) }
        dao.upsertVariants(entities)
        entities.map { it.canonicalKey }.distinct().forEach { key ->
            refreshCanonical(key, now)
        }
        dao.deleteVariantsOlderThan(now - VARIANT_TTL_MS)
        dao.deleteCanonicalOlderThan(now - CANONICAL_TTL_MS)
    }

    override suspend fun resolveByTrackId(trackId: String): CanonicalTrack? {
        val entity = dao.variantByTrackId(trackId)
            ?: dao.canonicalByPrimaryTrackId(trackId)?.let { canonical ->
                dao.variants(canonical.canonicalKey).firstOrNull { it.trackId == trackId }
            }
            ?: return null
        val rows = dao.variants(entity.canonicalKey)
        val decoded = rows.mapNotNull(::decode).sortedWith(playbackPreference)
        if (decoded.isEmpty()) return null
        val primary = decoded.first()
        val canonical = dao.canonical(entity.canonicalKey)
        return CanonicalTrack(
            key = entity.canonicalKey,
            title = canonical?.title ?: primary.title,
            artist = canonical?.artist ?: primary.artist,
            primary = primary,
            variants = decoded.map { track ->
                TrackVariant(
                    track = track,
                    confidence = rows.firstOrNull { it.trackId == track.id }?.confidence ?: 1.0,
                    lastCheckedAt = rows.firstOrNull { it.trackId == track.id }?.lastCheckedAt ?: 0L,
                )
            },
        )
    }

    private suspend fun refreshCanonical(key: String, now: Long) {
        val best = dao.variants(key).mapNotNull(::decode).maxWithOrNull(playbackPreference) ?: return
        dao.upsertCanonical(
            CanonicalTrackEntity(
                canonicalKey = key,
                title = best.title,
                artist = best.artist,
                primaryTrackId = best.id,
                updatedAt = now,
            ),
        )
    }

    private fun Track.toEntity(key: String, confidence: Double, now: Long) =
        TrackVariantEntity(
            canonicalKey = key,
            trackId = id,
            source = source.name,
            title = title,
            artist = artist,
            sourceUrl = transcodingUrl ?: id.takeIf { it.startsWith("http") },
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            confidence = confidence,
            lastCheckedAt = now,
            trackJson = json.encodeToString(Track.serializer(), this),
        )

    private fun decode(entity: TrackVariantEntity): Track? = runCatching {
        json.decodeFromString(Track.serializer(), entity.trackJson)
    }.getOrNull()

    private fun confidence(primary: Track, candidate: Track): Double {
        if (candidate.id == primary.id) return 1.0
        var score = 0.55
        val sameArtist = MusicNormalizer.normalizeArtist(candidate.artist) ==
            MusicNormalizer.normalizeArtist(primary.artist)
        if (sameArtist) score += 0.2
        if (primary.durationMs > 0 && candidate.durationMs > 0) {
            val delta = abs(primary.durationMs - candidate.durationMs)
            score += when {
                delta <= 2_000L -> 0.15
                delta <= 5_000L -> 0.10
                delta <= 10_000L -> 0.05
                delta <= 30_000L -> 0.0
                else -> -0.10
            }
        }
        if (candidate.streamUrl != null && candidate.previewSeconds == null) score += 0.10
        if (candidate.isSubscriptionOnly) score -= 0.15
        return score.coerceIn(0.0, 1.0)
    }

    private fun playbackScore(track: Track): Int {
        var score = 0
        if (track.streamUrl != null) score += 4
        if (track.transcodingUrl != null) score += 2
        if (track.previewSeconds == null) score += 2
        if (!track.isSubscriptionOnly) score += 2
        if (track.artworkUrl != null) score += 1
        return score
    }

    private val playbackPreference: Comparator<Track> =
        compareByDescending<Track> { playbackScore(it) }
            .thenByDescending { it.durationMs }

    companion object {
        private const val VARIANT_TTL_MS = 60L * 24 * 60 * 60 * 1000
        private const val CANONICAL_TTL_MS = 180L * 24 * 60 * 60 * 1000
    }
}
