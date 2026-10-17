package com.my.music.data.repository

import com.my.music.data.model.Track
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
     */
    suspend fun addToHistory(track: Track)

    /**
     * Очистить историю прослушиваний.
     */
    suspend fun clearHistory()
}
