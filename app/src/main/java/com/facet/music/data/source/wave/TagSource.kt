package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.data.repository.LastFmRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Находки по жанрам/тегам: tag.getTopTracks со случайной страницы.
 * Если жанров в профиле нет, теги берутся у артистов-семян.
 */
@Singleton
class TagSource @Inject constructor(
    private val lastFmRepository: LastFmRepository,
    private val resolver: LastFmTrackResolver,
    private val networkGate: WaveNetworkGate,
) : CandidateSource {

    override suspend fun candidates(
        plan: WaveSessionPlan,
        randomSeed: Long,
    ): List<Candidate> = supervisorScope {
        val random = Random(randomSeed)
        val seedGenres = plan.seedGenres
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { MusicNormalizer.normalizeWords(it) }
            .shuffled(random)
            .take(TAG_SEEDS)
            .toMutableList()

        if (seedGenres.isEmpty()) {
            for (artist in plan.seedArtists.shuffled(random).take(TAG_ARTISTS)) {
                val tags = networkGate.withPermit {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        lastFmRepository.getArtistTopTags(artist.displayName, TAG_LIMIT)
                            .getOrNull()
                            .orEmpty()
                    }
                }.orEmpty()
                seedGenres += tags
                if (seedGenres.size >= TAG_SEEDS) break
            }
        }
        if (seedGenres.isEmpty()) return@supervisorScope emptyList()

        val budget = AtomicInteger(0)
        seedGenres.distinctBy { MusicNormalizer.normalizeWords(it) }
            .map { tag -> async { fetchTag(tag, randomSeed, budget) } }
            .awaitAll()
            .flatten()
    }

    private suspend fun fetchTag(
        tag: String,
        randomSeed: Long,
        budget: AtomicInteger,
    ): List<Candidate> {
        val random = Random(randomSeed.toInt() xor tag.hashCode())
        val page = random.nextInt(1, PAGE_MAX + 1)
        val tracks = networkGate.withPermit {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                lastFmRepository.getTagTopTracks(tag, TOP_TRACKS_LIMIT, page)
                    .getOrNull()
                    .orEmpty()
            }
        }.orEmpty()
        return tracks.shuffled(random).take(TRACKS_PER_TAG).mapIndexed { index, track ->
            val playable = if (budget.getAndIncrement() < RESOLVE_BUDGET) {
                resolver.resolve(track)
            } else {
                resolver.metadata(track)
            }
            Candidate(
                track = playable,
                origin = CandidateOrigin.TAG,
                seedKey = MusicNormalizer.normalizeWords(tag),
                sourceRank = index,
            )
        }
    }

    private companion object {
        const val TAG_SEEDS = 2
        const val TAG_ARTISTS = 2
        const val TAG_LIMIT = 10
        const val PAGE_MAX = 5
        const val TOP_TRACKS_LIMIT = 20
        const val TRACKS_PER_TAG = 8
        const val RESOLVE_BUDGET = 2
        const val REQUEST_TIMEOUT_MS = 4_000L
    }
}
