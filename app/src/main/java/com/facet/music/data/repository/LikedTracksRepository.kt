package com.facet.music.data.repository

import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Track
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
     * Поток лайков, сохранённых в указанном сезоне (Winter / Spring / Summer / Autumn).
     * Основа секции «Воспоминания».
     */
    fun getLikedTracksBySeason(season: String): Flow<List<Track>>

    /**
     * Лайки сезона вместе с геометками — для крупной карточки «Воспоминания».
     */
    fun getSeasonMemories(season: String): Flow<List<SeasonMemory>>

    /**
     * Наблюдение за статусом лайка конкретного трека.
     */
    fun isLiked(trackId: String): Flow<Boolean>

    /**
     * Добавить трек в избранное.
     */
    suspend fun likeTrack(
        track: Track,
        latitude: Double? = null,
        longitude: Double? = null,
        season: String? = null,
        /** Время лайка; для импорта — реальная дата из источника. */
        likedAt: Long = System.currentTimeMillis(),
    )

    /** Обновить только дату лайка (импорт не должен терять сезон и геометку). */
    suspend fun updateLikeTime(trackId: String, likedAt: Long)

    /**
     * Удалить трек из избранного.
     */
    suspend fun unlikeTrack(trackId: String)

    /**
     * Переключить состояние лайка трека.
     * Геопозиция и сезон сохраняются только при постановке лайка.
     * Возвращает новое состояние (true — лайкнут, false — снят лайк).
     */
    suspend fun toggleLike(
        track: Track,
        latitude: Double? = null,
        longitude: Double? = null,
        season: String? = null,
    ): Boolean
}
