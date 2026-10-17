package com.my.music.data.local

import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.entity.toLikedEntity
import com.my.music.data.local.entity.toTrack
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

    override fun isLiked(trackId: String): Flow<Boolean> {
        return likedTracksDao.isLiked(trackId)
            .flowOn(Dispatchers.IO)
    }

    override suspend fun likeTrack(track: Track) = withContext(Dispatchers.IO) {
        likedTracksDao.insertLikedTrack(track.toLikedEntity())
    }

    override suspend fun unlikeTrack(trackId: String) = withContext(Dispatchers.IO) {
        likedTracksDao.deleteLikedTrack(trackId)
    }

    override suspend fun toggleLike(track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = likedTracksDao.getLikedTrack(track.id)
        if (existing != null) {
            likedTracksDao.deleteLikedTrack(track.id)
            false
        } else {
            likedTracksDao.insertLikedTrack(track.toLikedEntity())
            true
        }
    }
}
