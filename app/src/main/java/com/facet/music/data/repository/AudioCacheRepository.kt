package com.facet.music.data.repository

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Запись кэша прослушанного для экрана настроек и LRU-очистки.
 */
data class AudioCacheEntry(
    val trackId: String,
    val filePath: String,
    val sizeBytes: Long,
    val cachedAt: Long,
)

/**
 * Кэш прослушанного: файлы в приватном каталоге приложения.
 *
 * Лимит задаётся [MAX_BYTES]; при превышении удаляются записи с самым
 * старым [AudioCacheEntry] (LRU по времени последнего доступа).
 */
interface AudioCacheRepository {

    /** Все записи кэша, свежие сверху — для экрана в настройках. */
    fun observeEntries(): Flow<List<AudioCacheEntry>>

    /** Существующий файл кэша, если он есть на диске; обновляет время доступа. */
    suspend fun fileFor(trackId: String): File?

    suspend fun totalBytes(): Long

    /** Зарегистрировать скачанный файл; повторная запись заменяет старую. */
    suspend fun put(trackId: String, file: File)

    /** Путь для записи нового файла кэша (каталог создаётся при необходимости). */
    fun fileForWrite(trackId: String): File

    /** Удалить всё содержимое кэша. */
    suspend fun clear()

    /** Вытеснить старые файлы, пока суммарный размер не впишется в лимит. */
    suspend fun enforceLimit(maxBytes: Long = MAX_BYTES)

    companion object {
        /** Жёсткий лимит кэша — 500 МБ. */
        const val MAX_BYTES: Long = 500L * 1024 * 1024
    }
}
