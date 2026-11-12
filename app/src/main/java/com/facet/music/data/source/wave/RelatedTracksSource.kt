package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.TrackSeed
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class RelatedTracksSource @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val youTubeRepository: YouTubeRepository,
    private val networkGate: WaveNetworkGate,
) : CandidateSource {

    override suspend fun candidates(
        plan: WaveSessionPlan,
        randomSeed: Long,
    ): List<Candidate> = supervisorScope {
        val random = Random(randomSeed)
        val seeds = buildList {
            plan.radioSeed?.let { add(TrackSeed(it, 1.0, keyOf(it), artistKeyOf(it))) }
            addAll(plan.seedTracks)
        }.distinctBy { it.trackKey }
            .filter { sourceUrl(it.track) != null }
            .let { rows ->
                if (rows.size <= MAX_SEEDS) rows else weightedTake(rows, MAX_SEEDS, random)
            }

        seeds.map { seed ->
            async { fetchSeed(seed, randomSeed) }
        }.awaitAll().flatten()
    }

    private suspend fun fetchSeed(seed: TrackSeed, randomSeed: Long): List<Candidate> {
        val url = sourceUrl(seed.track) ?: return emptyList()
        val related = networkGate.withPermit {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                when (seed.track.source) {
                    Source.SOUNDCLOUD -> soundCloudRepository
                        .getRelated(url, RELATED_LIMIT)
                        .getOrNull()

                    Source.YOUTUBE -> youTubeRepository
                        .getRelated(url, RELATED_LIMIT)
                        .getOrNull()

                    else -> null
                }
            }
        }.orEmpty()
            .filter { it.id.isNotBlank() && keyOf(it) != seed.trackKey }
            .distinctBy { keyOf(it) }
        if (related.isEmpty()) return emptyList()

        val random = Random(randomSeed.toInt() xor seed.trackKey.hashCode())
        val shift = if (related.size <= 3) 0 else random.nextInt(0, minOf(4, related.size / 2))
        return related.drop(shift).take(TRACKS_PER_SEED).mapIndexed { index, track ->
            Candidate(
                track = track,
                origin = CandidateOrigin.RELATED,
                seedKey = seed.trackKey,
                sourceRank = index,
            )
        }
    }

    private fun weightedTake(
        rows: List<TrackSeed>,
        count: Int,
        random: Random,
    ): List<TrackSeed> {
        val remaining = rows.toMutableList()
        val result = mutableListOf<TrackSeed>()
        repeat(count.coerceAtMost(remaining.size)) {
            val total = remaining.sumOf { it.weight.coerceAtLeast(0.0) }
            var point = random.nextDouble() * total
            var index = 0
            if (total > 0.0) {
                for (i in remaining.indices) {
                    point -= remaining[i].weight.coerceAtLeast(0.0)
                    if (point <= 0.0) {
                        index = i
                        break
                    }
                }
            } else {
                index = random.nextInt(remaining.size)
            }
            result += remaining.removeAt(index)
        }
        return result
    }

    private fun sourceUrl(track: Track): String? {
        if (track.source != Source.SOUNDCLOUD && track.source != Source.YOUTUBE) return null
        return track.transcodingUrl?.takeIf { it.startsWith("http") }
            ?: track.id.takeIf { it.startsWith("http") }
    }

    private fun keyOf(track: Track): String =
        MusicNormalizer.trackKey(track.artist, track.title).ifBlank { "id:${track.id}" }

    private fun artistKeyOf(track: Track): String =
        MusicNormalizer.normalizeArtist(track.artist).ifBlank { "track:${track.id}" }

    private companion object {
        const val MAX_SEEDS = 5
        const val RELATED_LIMIT = 30
        const val TRACKS_PER_SEED = 8
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}
