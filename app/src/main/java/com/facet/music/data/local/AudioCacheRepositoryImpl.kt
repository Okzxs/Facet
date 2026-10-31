package com.facet.music.data.local

import android.content.Context
import com.facet.music.data.local.dao.AudioCacheDao
import com.facet.music.data.local.entity.AudioCacheEntity
import com.facet.music.data.repository.AudioCacheEntry
import com.facet.music.data.repository.AudioCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файловый кэш в `cacheDir/audio_cache` (система может очистить его сама)
 * и метаданные в Room. Файлы именуются по безопасному хэшу id трека.
 */
@Singleton
class AudioCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AudioCacheDao,
) : AudioCacheRepository {

    private val directory: File
        get() = File(context.cacheDir, DIRECTORY_NAME)

    override fun observeEntries(): Flow<List<AudioCacheEntry>> =
        dao.observeAll().map { rows ->
            rows.map {
                AudioCacheEntry(
                    trackId = it.trackId,
                    filePath = it.filePath,
                    sizeBytes = it.sizeBytes,
                    cachedAt = it.cachedAt,
                )
            }
        }

    override suspend fun fileFor(trackId: String): File? = withContext(Dispatchers.IO) {
        val row = dao.get(trackId) ?: return@withContext null
        val file = File(row.filePath)
        if (!file.exists() || file.length() <= 0L) {
            // Файл вычистила система — запись больше не нужна.
            dao.delete(trackId)
            return@withContext null
        }
        dao.touch(trackId, System.currentTimeMillis())
        file
    }

    override suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dao.totalSizeBytes()
    }

    override suspend fun put(trackId: String, file: File) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsert(
            AudioCacheEntity(
                trackId = trackId,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                cachedAt = now,
                lastAccessAt = now,
            ),
        )
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    override suspend fun enforceLimit(maxBytes: Long) = withContext(Dispatchers.IO) {
        var total = dao.totalSizeBytes()
        if (total <= maxBytes) return@withContext
        // Удаляем самые давно использованные, пока не впишемся в лимит.
        for (row in dao.oldest(OLDEST_BATCH)) {
            if (total <= maxBytes) break
            runCatching { File(row.filePath).delete() }
            dao.delete(row.trackId)
            total -= row.sizeBytes
        }
    }

    /** Файл под новый кэш; расширение не важно — плеер читает по MIME. */
    override fun fileForWrite(trackId: String): File {
        val dir = directory
        if (!dir.exists()) dir.mkdirs()
        return File(dir, trackId.hashCode().toUInt().toString(16) + FILE_EXTENSION)
    }

    private companion object {
        const val DIRECTORY_NAME = "audio_cache"
        const val FILE_EXTENSION = ".audio"
        const val OLDEST_BATCH = 20
    }
}
