package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.data.repository.ArtistRepository
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
 * Похожие артисты Last.fm, выбранные softmax-выборкой из вкуса. Запросы
 * похожих и топ-треков идут параллельно; для каждого артиста берём случайную
 * страницу и 1–2 трека, первые найденные полные потоки резолвим в YouTube.
 */
@Singleton
class SimilarArtistsSource @Inject constructor(
    private val lastFmRepository: LastFmRepository,
    private val artistRepository: ArtistRepository,
    private val resolver: LastFmTrackResolver,
    private val networkGate: WaveNetworkGate,
) : CandidateSource {

    override suspend fun candidates(
        plan: WaveSessionPlan,
        randomSeed: Long,
    ): List<Candidate> = supervisorScope {
        val random = Random(randomSeed)
        val seedNames = plan.seedArtists
            .filter { it.displayName.isNotBlank() }
            .shuffled(random)
            .take(SEED_ARTISTS)
        if (seedNames.isEmpty()) return@supervisorScope emptyList()

        val similar = LinkedHashMap<String, String>()
        seedNames.map { seed ->
            async { fetchSimilar(seed.displayName) }
        }.awaitAll().forEach { names ->
            names.forEach { name ->
                val key = MusicNormalizer.normalizeArtist(name)
                if (key.isNotBlank()) similar.putIfAbsent(key, name)
            }
        }

        val blocked = plan.history.blockedArtistKeys
        val previous = plan.previousSeedKeys
        val previousArtists = plan.previousSeedArtistKeys
        val available = similar
            .filterKeys { key ->
                key !in blocked &&
                    key !in previousArtists &&
                    previous.none { it.startsWith("$key|") }
            }
            .values
            .toList()

        if (available.isEmpty()) {
            return@supervisorScope fallbackPopular(seedNames.map { it.displayName }, random)
        }

        val count = random.nextInt(PICK_MIN, PICK_MAX + 1).coerceAtMost(available.size)
        val chosen = available.shuffled(random).take(count)
        val budget = AtomicInteger(0)
        val result = chosen.mapIndexed { index, artist ->
            async { fetchArtist(artist, index, randomSeed, budget) }
        }.awaitAll().flatten()

        if (result.isEmpty()) fallbackPopular(chosen, random) else result
    }

    private suspend fun fetchSimilar(artist: String): List<String> =
        networkGate.withPermit {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                lastFmRepository.getSimilarArtists(artist, SIMILAR_LIMIT)
                    .getOrNull()
                    .orEmpty()
            }
        }.orEmpty()

    private suspend fun fetchArtist(
        artist: String,
        index: Int,
        randomSeed: Long,
        budget: AtomicInteger,
    ): List<Candidate> {
        val random = Random(randomSeed.toInt() xor artist.hashCode())
        val page = random.nextInt(1, TOP_TRACKS_PAGES + 1)
        val top = networkGate.withPermit {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                lastFmRepository.getArtistTopTracks(artist, TOP_TRACKS_LIMIT, page)
                    .getOrNull()
                    .orEmpty()
            }
        }.orEmpty()
        val picks = top.shuffled(random)
            .take(random.nextInt(TRACKS_MIN, TRACKS_MAX + 1))
        if (picks.isEmpty()) return emptyList()

        return picks.mapIndexed { trackIndex, track ->
            val playable = if (budget.getAndIncrement() < RESOLVE_BUDGET) {
                resolver.resolve(track)
            } else {
                resolver.metadata(track)
            }
            Candidate(
                track = playable,
                origin = CandidateOrigin.SIMILAR_ARTIST,
                seedKey = MusicNormalizer.normalizeArtist(artist),
                sourceRank = index + trackIndex,
            )
        }
    }

    private suspend fun fallbackPopular(
        artists: List<String>,
        random: Random,
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        for (artist in artists.shuffled(random).take(FALLBACK_ARTISTS)) {
            val found = networkGate.withPermit {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    artistRepository.resolveByName(artist).getOrNull()
                }
            } ?: continue
            val tracks = networkGate.withPermit {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    artistRepository.popularTracks(found.source, found.url, FALLBACK_TRACKS)
                        .getOrNull()
                        .orEmpty()
                }
            }.orEmpty()
            tracks.forEachIndexed { index, track ->
                result += Candidate(
                    track = track,
                    origin = CandidateOrigin.SIMILAR_ARTIST,
                    seedKey = MusicNormalizer.normalizeArtist(found.name),
                    sourceRank = index,
                )
            }
        }
        return result
    }

    private companion object {
        const val SEED_ARTISTS = 4
        const val SIMILAR_LIMIT = 30
        const val PICK_MIN = 6
        const val PICK_MAX = 8
        const val TOP_TRACKS_LIMIT = 20
        const val TOP_TRACKS_PAGES = 2
        const val TRACKS_MIN = 1
        const val TRACKS_MAX = 2
        const val RESOLVE_BUDGET = 4
        const val FALLBACK_ARTISTS = 2
        const val FALLBACK_TRACKS = 2
        const val REQUEST_TIMEOUT_MS = 1_500L
    }
}
