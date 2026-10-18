package com.my.music.data.repository

import com.my.music.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для получения чартов SoundCloud («New & hot», «Top 50»).
 * Поддерживает кэширование в Room и 30-минутный TTL.
 */
interface SoundCloudChartsRepository {

    /**
     * Реактивный поток треков чарта "New & hot" из локального кэша Room.
     */
    fun observeNewAndHot(): Flow<List<Track>>

    /**
     * Реактивный поток треков чарта "Top 50" из локального кэша Room.
     */
    fun observeTop50(): Flow<List<Track>>

    /**
     * Получить или обновить треки "New & hot".
     * Если данные в кэше моложе TTL (30 минут) и [forceRefresh] == false, возвращает кэш.
     * Иначе загружает из NewPipe, обновляет Room и возвращает результат.
     */
    suspend fun getNewAndHot(forceRefresh: Boolean = false): Result<List<Track>>

    /**
     * Получить или обновить треки "Top 50".
     * Если данные в кэше моложе TTL (30 минут) и [forceRefresh] == false, возвращает кэш.
     * Иначе загружает из NewPipe, обновляет Room и возвращает результат.
     */
    suspend fun getTop50(forceRefresh: Boolean = false): Result<List<Track>>
}
