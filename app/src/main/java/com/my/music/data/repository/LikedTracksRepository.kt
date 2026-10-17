package com.my.music.data.repository

import com.my.music.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий избранных (лайкнутых) треков.
 */
interface LikedTracksRepository {

    /**
     * Поток списка всех лайкнутых треков (отсортированных от новых к старым).
     */
    fun getLikedTracks(): Flow<List<Track>>

    /**
     * Наблюдение за статусом лайка конкретного трека.
     */
    fun isLiked(trackId: String): Flow<Boolean>

    /**
     * Добавить трек в избранное.
     */
    suspend fun likeTrack(track: Track)

    /**
     * Удалить трек из избранного.
     */
    suspend fun unlikeTrack(trackId: String)

    /**
     * Переключить состояние лайка трека.
     * Возвращает новое состояние (true — лайкнут, false — снят лайк).
     */
    suspend fun toggleLike(track: Track): Boolean
}
