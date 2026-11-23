package com.facet.music.core.wave

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveExplanationFactoryTest {

    private val track = Track(
        id = "sc:1",
        source = Source.SOUNDCLOUD,
        title = "One More Time",
        artist = "Daft Punk",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 320_000L,
        genre = "French House",
    )

    private fun details(
        origin: CandidateOrigin = CandidateOrigin.RELATED,
        artistAffinity: Double = 0.0,
        genreAffinity: Double = 0.0,
        novelty: Double = 1.0,
        popularity: Double = 0.0,
        penalty: Double = 0.0,
        seedKey: String? = "daft punk|aerodynamic",
    ) = WaveCandidateDetails(
        trackKey = "daft punk|one more time",
        origin = origin,
        seedKey = seedKey,
        artistAffinity = artistAffinity,
        genreAffinity = genreAffinity,
        originPrior = origin.prior,
        novelty = novelty,
        seedWeight = 0.5,
        popularity = popularity,
        penalty = penalty,
        noise = 0.0,
        score = 1.2,
    )

    @Test
    fun `related track explains the seed track`() {
        val explanation = WaveExplanationFactory.explain(
            details = details(),
            track = track,
            seedArtistName = "Daft Punk",
            seedTrackTitle = "Aerodynamic",
        )

        assertEquals(track.id, explanation.trackId)
        assertTrue(explanation.reasons.any {
            it.type == WaveReasonType.SEED_TRACK && it.value == "Aerodynamic"
        })
    }

    @Test
    fun `similar artist reason carries the artist name`() {
        val explanation = WaveExplanationFactory.explain(
            details = details(origin = CandidateOrigin.SIMILAR_ARTIST),
            track = track,
            seedArtistName = "Justice",
        )

        assertTrue(explanation.reasons.any {
            it.type == WaveReasonType.SIMILAR_ARTIST && it.value == "Justice"
        })
    }

    @Test
    fun `genre affinity and popularity become reasons`() {
        val explanation = WaveExplanationFactory.explain(
            details = details(genreAffinity = 0.4, popularity = 0.8),
            track = track,
        )

        assertTrue(explanation.reasons.any {
            it.type == WaveReasonType.GENRE && it.value == "French House"
        })
        assertTrue(explanation.reasons.any { it.type == WaveReasonType.POPULAR })
    }

    @Test
    fun `played before becomes familiar and heavy rotation becomes penalty`() {
        val explanation = WaveExplanationFactory.explain(
            details = details(novelty = 0.0, penalty = 0.3),
            track = track,
        )

        assertTrue(explanation.reasons.any { it.type == WaveReasonType.FAMILIAR })
        assertTrue(explanation.reasons.any { it.type == WaveReasonType.PENALTY })
        assertTrue(explanation.reasons.none { it.type == WaveReasonType.NOVELTY })
    }

    @Test
    fun `reasons are unique by type and value`() {
        val explanation = WaveExplanationFactory.explain(
            details = details(artistAffinity = 0.5),
            track = track,
            seedArtistName = "Daft Punk",
        )

        val keys = explanation.reasons.map { it.type to it.value }
        assertEquals(keys.size, keys.distinct().size)
    }
}
