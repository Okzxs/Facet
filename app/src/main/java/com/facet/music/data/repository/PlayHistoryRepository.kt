package com.facet.music.data.repository

import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

interface PlayHistoryRepository {

    fun getPlayHistory(): Flow<List<Track>>

    suspend fun addToHistory(track: Track, playedFraction: Float? = null)

    suspend fun updatePlayedFraction(trackId: String, fraction: Float)

    suspend fun clearHistory()
}
