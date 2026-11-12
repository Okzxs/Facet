package com.facet.music.data.source.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.CandidateSource
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.WaveSessionPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FamiliarSource @Inject constructor(
    private val clock: Clock,
) : CandidateSource {

    override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
        if (plan.isRadio) return emptyList()
        val random = Random(randomSeed)
        val now = clock.nowMs()
        val shown = plan.history.impressions
            .filter { it.shownAt >= now - SHOWN_DAYS_MS }
            .associate { it.trackKey to it.shownAt }
        val played = plan.history.plays.associate {
            MusicNormalizer.trackKey(it.track.artist, it.track.title) to it.atMs
        }

        return plan.history.familiarTracks
            .asSequence()
            .filter { row ->
                val key = MusicNormalizer.trackKey(row.track.artist, row.track.title)
                    .ifBlank { "id:${row.track.id}" }
                val lastPlay = played[key]
                val lastShown = shown[key]
                val oldPlay = lastPlay == null || now - lastPlay >= UNPLAYED_DAYS_MS * DAY_MS
                val oldShow = lastShown == null || now - lastShown >= SHOWN_DAYS_MS
                oldPlay && oldShow && row.track.durationMs in 60_000L..720_000L
            }
            .distinctBy { MusicNormalizer.trackKey(it.track.artist, it.track.title) }
            .shuffled(random)
            .take(MAX_CANDIDATES)
            .mapIndexed { index, row ->
                Candidate(
                    track = row.track,
                    origin = CandidateOrigin.FAMILIAR,
                    seedKey = MusicNormalizer.normalizeArtist(row.track.artist),
                    sourceRank = index,
                )
            }
            .toList()
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val UNPLAYED_DAYS_MS = 21
        const val SHOWN_DAYS_MS = 30 * DAY_MS
        const val MAX_CANDIDATES = 6
    }
}
