package com.facet.music.data.repository

import com.facet.music.data.model.CanonicalTrack
import com.facet.music.data.model.Track

interface CanonicalTrackRepository {

    fun canonicalKey(track: Track): String

    suspend fun canonicalize(tracks: List<Track>): List<CanonicalTrack>

    suspend fun variants(canonicalKey: String): List<Track>

    suspend fun bestForPlayback(canonicalKey: String): Track?

    suspend fun fallbackFor(track: Track): Track?

    suspend fun recordVariant(track: Track, confidence: Double = DEFAULT_CONFIDENCE)

    suspend fun recordVariants(tracks: List<Track>, confidence: Double = DEFAULT_CONFIDENCE)

    suspend fun resolveByTrackId(trackId: String): CanonicalTrack?

    companion object {
        const val DEFAULT_CONFIDENCE = 1.0
    }
}
