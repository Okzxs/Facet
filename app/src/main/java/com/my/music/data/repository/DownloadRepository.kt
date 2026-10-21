package com.my.music.data.repository

import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Track
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Репозиторий офлайн-режима: хранит записи о скачанных треках
 * и отдаёт путь к локальному файлу для воспроизведения.
 */
interface DownloadRepository {

    /** Идентификаторы всех скачанных треков — для бейджей в списках. */
    fun observeDownloadedIds(): Flow<Set<String>>

    /** Скачан ли конкретный трек (наблюдаемый флаг для кнопки в плеере). */
    fun observeIsDownloaded(trackId: String): Flow<Boolean>

    /** Все скачанные треки в порядке убывания времени скачивания. */
    fun observeDownloadedTracks(): Flow<List<DownloadedTrack>>

    suspend fun getDownloadedTrack(trackId: String): DownloadedTrack?

    /**
     * Локальный файл трека, если запись есть и файл реально лежит на диске.
     * Битые записи (файл удалён) автоматически вычищаются.
     */
    suspend fun getLocalFile(trackId: String): File?

    /** Сохранить запись о скачанном треке. */
    suspend fun save(track: Track, localFilePath: String)

    /** Удалить запись и сам файл. */
    suspend fun remove(trackId: String)
}
