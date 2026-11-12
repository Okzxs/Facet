package com.facet.music.data.repository

import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

interface LikedTracksRepository {

    fun getLikedTracks(): Flow<List<Track>>

    fun getLikedTracksBySeason(season: String): Flow<List<Track>>

    fun getSeasonMemories(season: String): Flow<List<SeasonMemory>>

    fun isLiked(trackId: String): Flow<Boolean>

    suspend fun likeTrack(
        track: Track,
        latitude: Double? = null,
        longitude: Double? = null,
        season: String? = null,
        likedAt: Long = System.currentTimeMillis(),
    )

    suspend fun updateLikeTime(trackId: String, likedAt: Long)

    suspend fun unlikeTrack(trackId: String)

    suspend fun toggleLike(
        track: Track,
        latitude: Double? = null,
        longitude: Double? = null,
        season: String? = null,
    ): Boolean
}
