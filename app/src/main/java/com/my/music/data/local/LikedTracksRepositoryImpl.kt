package com.my.music.data.local

import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.entity.toLikedEntity
import com.my.music.data.local.entity.toSeasonMemory
import com.my.music.data.local.entity.toTrack
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория лайкнутых треков на базе Room.
 */
@Singleton
class LikedTracksRepositoryImpl @Inject constructor(
    private val likedTracksDao: LikedTracksDao,
) : LikedTracksRepository {

    override fun getLikedTracks(): Flow<List<Track>> {
        return likedTracksDao.getLikedTracks()
            .map { list -> list.map { it.toTrack() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getLikedTracksBySeason(season: String): Flow<List<Track>> {
        return likedTracksDao.getLikedTracksBySeason(season)
            .map { list -> list.map { it.toTrack() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getSeasonMemories(season: String): Flow<List<SeasonMemory>> {
        return likedTracksDao.getLikedTracksBySeason(season)
            .map { list -> list.map { it.toSeasonMemory() } }
            .flowOn(Dispatchers.IO)
    }

    override fun isLiked(trackId: String): Flow<Boolean> {
        return likedTracksDao.isLiked(trackId)
            .flowOn(Dispatchers.IO)
    }

    override suspend fun likeTrack(
        track: Track,
        latitude: Double?,
        longitude: Double?,
        season: String?,
    ) = withContext(Dispatchers.IO) {
        likedTracksDao.insertLikedTrack(
            track.toLikedEntity(
                latitude = latitude,
                longitude = longitude,
                season = season,
            ),
        )
    }

    override suspend fun unlikeTrack(trackId: String) = withContext(Dispatchers.IO) {
        likedTracksDao.deleteLikedTrack(trackId)
    }

    override suspend fun toggleLike(
        track: Track,
        latitude: Double?,
        longitude: Double?,
        season: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        likedTracksDao.toggleLikedTrack(
            track.toLikedEntity(
                latitude = latitude,
                longitude = longitude,
                season = season,
            ),
        )
    }
}
