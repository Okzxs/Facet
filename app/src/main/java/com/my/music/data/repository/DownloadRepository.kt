package com.my.music.data.repository

import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий офлайн-режима: хранит записи о скачанных треках
 * и отдаёт локальные файлы для воспроизведения.
 */
interface DownloadRepository {

    /** Идентификаторы всех скачанных треков — для бейджей в списках. */
    fun observeDownloadedIds(): Flow<Set<String>>

    /** Скачан ли конкретный трек (наблюдаемый флаг для кнопки в плеере). */
    fun observeIsDownloaded(trackId: String): Flow<Boolean>

    /** Все скачанные треки в порядке убывания времени скачивания. */
    fun observeDownloadedTracks(): Flow<List<DownloadedTrack>>

    /**
     * Скачанный трек для воспроизведения, если локальный файл реально на месте.
     * Битые записи (файл удалён) автоматически вычищаются.
     */
    suspend fun getDownloadedTrack(trackId: String): DownloadedTrack?

    /** Сохранить запись о скачанном треке после успешной загрузки. */
    suspend fun save(track: Track, mediaUrl: String, mimeType: String)

    /** Удалить запись и связанный с ней локальный файл. */
    suspend fun remove(trackId: String)
}
