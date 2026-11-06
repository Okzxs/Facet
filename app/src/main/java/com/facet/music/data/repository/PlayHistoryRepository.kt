package com.facet.music.data.repository

import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий истории воспроизведения.
 */
interface PlayHistoryRepository {

    /**
     * Поток списка последних прослушанных треков (до 50 штук, новые первыми).
     */
    fun getPlayHistory(): Flow<List<Track>>

    /**
     * Записать трек в историю (или обновить timestamp, если уже был).
     * [playedFraction] — доля проигранного на момент записи.
     */
    suspend fun addToHistory(track: Track, playedFraction: Float? = null)

    /** Уточнить долю прослушивания после завершения трека. */
    suspend fun updatePlayedFraction(trackId: String, fraction: Float)

    /**
     * Очистить историю прослушиваний.
     */
    suspend fun clearHistory()
}
