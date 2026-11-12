package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.data.local.dao.FollowedArtistDao
import com.facet.music.data.model.Source
import com.facet.music.data.repository.ArtistRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FollowedArtistsSource @Inject constructor(
    private val followedArtistDao: FollowedArtistDao,
    private val artistRepository: ArtistRepository,
    private val clock: Clock,
    private val networkGate: WaveNetworkGate,
) : CandidateSource {

    override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
        if (plan.isRadio) return emptyList()
        val random = Random(randomSeed)
        val now = clock.nowMs()
        val currentYear = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
        val followed = followedArtistDao.getAll()
            .filter { now - it.followedAt <= FRESH_DAYS_MS }
            .shuffled(random)
            .take(MAX_ARTISTS)
        if (followed.isEmpty()) return emptyList()

        val result = mutableListOf<Candidate>()
        for (entity in followed) {
            if (result.size >= MAX_TRACKS) break
            val source = runCatching { Source.valueOf(entity.source) }.getOrNull() ?: continue
            val releases = networkGate.withPermit {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    artistRepository.releases(source, entity.url).getOrNull().orEmpty()
                }
            }.orEmpty()
            val fresh = releases
                .filter { release ->
                    val year = release.year?.toIntOrNull()
                    year == null || year >= currentYear - 1
                }
                .take(MAX_TRACKS)

            val fromReleases = fresh.flatMap { release ->
                networkGate.withPermit {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        artistRepository.releaseTracks(source, release.url)
                            .getOrNull()
                            .orEmpty()
                    }
                }.orEmpty().shuffled(random).take(1)
            }

            val tracks = fromReleases.ifEmpty {
                networkGate.withPermit {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        artistRepository.allTracks(source, entity.url, limit = 10)
                            .getOrNull()
                            ?.tracks
                            .orEmpty()
                    }
                }.orEmpty()
                    .ifEmpty {
                        networkGate.withPermit {
                            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                                artistRepository.popularTracks(source, entity.url, 5)
                                    .getOrNull()
                                    .orEmpty()
                            }
                        }.orEmpty()
                    }
                    .shuffled(random)
                    .take(MAX_TRACKS)
            }

            tracks
                .filterNot { it.isSubscriptionOnly }
                .take(MAX_TRACKS - result.size)
                .forEachIndexed { index, track ->
                    result += Candidate(
                        track = track,
                        origin = CandidateOrigin.FOLLOWED,
                        seedKey = MusicNormalizer.normalizeArtist(entity.name),
                        sourceRank = index,
                    )
                }
        }
        return result
    }

    private companion object {
        const val FRESH_DAYS_MS = 60 * 86_400_000L
        const val MAX_ARTISTS = 2
        const val MAX_TRACKS = 2
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}
