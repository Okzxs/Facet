package com.facet.music.core.wave

import com.facet.music.data.model.Track

object WaveExplanationFactory {

    fun explain(
        details: WaveCandidateDetails,
        track: Track,
        seedArtistName: String? = null,
        seedTrackTitle: String? = null,
    ): WaveExplanation {
        val reasons = mutableListOf<WaveReason>()
        when (details.origin) {
            CandidateOrigin.RELATED -> reasons += WaveReason(
                type = if (seedTrackTitle != null) WaveReasonType.SEED_TRACK else WaveReasonType.SEED_ARTIST,
                value = seedTrackTitle ?: seedArtistName ?: track.artist,
                weight = details.originPrior,
            )
            CandidateOrigin.SIMILAR_ARTIST -> reasons += WaveReason(
                type = WaveReasonType.SIMILAR_ARTIST,
                value = seedArtistName ?: track.artist,
                weight = details.originPrior,
            )
            CandidateOrigin.FOLLOWED -> reasons += WaveReason(
                type = WaveReasonType.FOLLOWED,
                value = track.artist,
                weight = details.originPrior,
            )
            CandidateOrigin.TAG -> reasons += WaveReason(
                type = WaveReasonType.GENRE,
                value = track.genre,
                weight = details.originPrior,
            )
            CandidateOrigin.SECOND_HOP -> reasons += WaveReason(
                type = WaveReasonType.SECOND_HOP,
                value = seedArtistName ?: track.artist,
                weight = details.originPrior,
            )
            CandidateOrigin.FAMILIAR -> reasons += WaveReason(
                type = WaveReasonType.FAMILIAR,
                value = null,
                weight = details.originPrior,
            )
        }
        if (details.artistAffinity > AFFINITY_THRESHOLD) {
            reasons += WaveReason(
                type = WaveReasonType.SEED_ARTIST,
                value = track.artist,
                weight = details.artistAffinity,
            )
        }
        if (details.genreAffinity > AFFINITY_THRESHOLD && !track.genre.isNullOrBlank()) {
            reasons += WaveReason(
                type = WaveReasonType.GENRE,
                value = track.genre,
                weight = details.genreAffinity,
            )
        }
        if (details.novelty > 0.5) {
            reasons += WaveReason(WaveReasonType.NOVELTY, weight = details.novelty)
        } else {
            reasons += WaveReason(WaveReasonType.FAMILIAR, weight = 1.0 - details.novelty)
        }
        if (details.popularity > POPULARITY_THRESHOLD) {
            reasons += WaveReason(WaveReasonType.POPULAR, weight = details.popularity)
        }
        if (details.penalty > 0.0) {
            reasons += WaveReason(
                type = WaveReasonType.PENALTY,
                value = track.artist,
                weight = details.penalty,
            )
        }
        val unique = reasons
            .distinctBy { it.type to it.value }
            .sortedByDescending { it.weight }
        return WaveExplanation(trackId = track.id, score = details.score, reasons = unique)
    }

    private const val AFFINITY_THRESHOLD = 0.15
    private const val POPULARITY_THRESHOLD = 0.5
}
