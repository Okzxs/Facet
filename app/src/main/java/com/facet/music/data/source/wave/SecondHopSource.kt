package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.data.repository.LastFmRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Разведка второго шага: похожие на похожих. Артисты, которых нет ни в
 * профиле вкуса, ни в истории, дают шанс выйти за пределы привычного круга.
 */
@Singleton
class SecondHopSource @Inject constructor(
    private val lastFmRepository: LastFmRepository,
    private val resolver: LastFmTrackResolver,
    private val networkGate: WaveNetworkGate,
) : CandidateSource {

    override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
        val random = Random(randomSeed)
        val known = buildSet {
            addAll(plan.profile.artists.keys)
            addAll(plan.previousSeedArtistKeys)
            plan.history.plays.mapTo(this) { MusicNormalizer.normalizeArtist(it.track.artist) }
            addAll(plan.history.blockedArtistKeys)
        }
        val seeds = plan.seedArtists
            .filter { it.displayName.isNotBlank() }
            .shuffled(random)
            .take(random.nextInt(SEED_MIN, SEED_MAX + 1))
        if (seeds.isEmpty()) return emptyList()

        val result = mutableListOf<Candidate>()
        var resolved = 0
        for (seed in seeds) {
            val firstHop = similar(seed.displayName)
                .filter { MusicNormalizer.normalizeArtist(it) !in known }
                .shuffled(random)
                .take(random.nextInt(BRIDGE_MIN, BRIDGE_MAX + 1))
            if (firstHop.isEmpty()) continue

            for (bridge in firstHop) {
                val secondHop = similar(bridge)
                    .filter { MusicNormalizer.normalizeArtist(it) !in known }
                    .shuffled(random)
                    .take(random.nextInt(RESULT_MIN, RESULT_MAX + 1))
                for (artist in secondHop) {
                    val tracks = networkGate.withPermit {
                        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                            lastFmRepository.getArtistTopTracks(artist, TOP_TRACKS, 1)
                                .getOrNull()
                                .orEmpty()
                        }
                    }.orEmpty()
                    tracks.shuffled(random).take(2).forEachIndexed { index, track ->
                        val playable = if (resolved < RESOLVE_BUDGET) {
                            resolved++
                            resolver.resolve(track)
                        } else {
                            resolver.metadata(track)
                        }
                        result += Candidate(
                            track = playable,
                            origin = CandidateOrigin.SECOND_HOP,
                            seedKey = MusicNormalizer.normalizeArtist(bridge),
                            sourceRank = index,
                        )
                    }
                }
            }
        }
        return result
    }

    private suspend fun similar(artist: String): List<String> =
        networkGate.withPermit {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                lastFmRepository.getSimilarArtists(artist, SIMILAR_LIMIT)
                    .getOrNull()
                    .orEmpty()
            }
        }.orEmpty()

    private companion object {
        const val SEED_MIN = 1
        const val SEED_MAX = 2
        const val BRIDGE_MIN = 1
        const val BRIDGE_MAX = 2
        const val RESULT_MIN = 2
        const val RESULT_MAX = 3
        const val SIMILAR_LIMIT = 10
        const val TOP_TRACKS = 5
        const val RESOLVE_BUDGET = 2
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}
