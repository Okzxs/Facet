package com.facet.music.data.repository

import com.facet.music.data.model.Track

data class TasteWeight(
    val value: String,
    val weight: Double,
)

enum class TasteContext {
    NORMAL,
    WAVE,
}

interface TasteRepository {

    suspend fun onTrackFinished(
        track: Track,
        playedFraction: Float,
        context: TasteContext = TasteContext.NORMAL,
    )

    suspend fun onSkip(
        track: Track,
        playedMs: Long,
        context: TasteContext = TasteContext.NORMAL,
    )

    suspend fun onLike(track: Track)

    suspend fun onUnlike(track: Track)

    suspend fun onDislike(track: Track)

    suspend fun getTopGenres(limit: Int = 5): List<TasteWeight>

    suspend fun getTopArtists(limit: Int = 5): List<TasteWeight>
}
