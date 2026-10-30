package com.facet.music.data.local

import com.facet.music.data.local.dao.LikedTracksDao
import com.facet.music.data.local.dao.PlayHistoryDao
import com.facet.music.data.local.entity.toHistoryEntity
import com.facet.music.data.local.entity.toTrack
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlayHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория истории воспроизведения на базе Room.
 * Автоматически актуализирует статус liked у треков через связку с LikedTracksDao.
 */
@Singleton
class PlayHistoryRepositoryImpl @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val likedTracksDao: LikedTracksDao,
) : PlayHistoryRepository {

    override fun getPlayHistory(): Flow<List<Track>> {
        return combine(
            playHistoryDao.getPlayHistory(limit = 50),
            likedTracksDao.getLikedTracks(),
        ) { historyList, likedList ->
            val likedIds = likedList.map { it.id }.toSet()
            historyList.map { entity ->
                entity.toTrack(isLiked = entity.id in likedIds)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun addToHistory(track: Track) = withContext(Dispatchers.IO) {
        playHistoryDao.recordPlay(track.toHistoryEntity(), keepCount = 50)
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        playHistoryDao.clearHistory()
    }
}
