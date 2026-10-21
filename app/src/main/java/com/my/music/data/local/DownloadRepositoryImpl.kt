package com.my.music.data.local

import com.my.music.data.local.dao.DownloadedTracksDao
import com.my.music.data.local.entity.toDomain
import com.my.music.data.local.entity.toDownloadEntity
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория скачанных треков на базе Room.
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadedTracksDao: DownloadedTracksDao,
) : DownloadRepository {

    override fun observeDownloadedIds(): Flow<Set<String>> {
        return downloadedTracksDao.getDownloadedIds()
            .map { ids -> ids.toSet() }
            .flowOn(Dispatchers.IO)
    }

    override fun observeIsDownloaded(trackId: String): Flow<Boolean> {
        return downloadedTracksDao.isDownloaded(trackId)
            .flowOn(Dispatchers.IO)
    }

    override fun observeDownloadedTracks(): Flow<List<DownloadedTrack>> {
        return downloadedTracksDao.getAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getDownloadedTrack(trackId: String): DownloadedTrack? {
        return withContext(Dispatchers.IO) {
            downloadedTracksDao.getById(trackId)?.toDomain()
        }
    }

    override suspend fun getLocalFile(trackId: String): File? = withContext(Dispatchers.IO) {
        val entry = downloadedTracksDao.getById(trackId) ?: return@withContext null
        val file = File(entry.localFilePath)
        if (file.exists() && file.length() > 0L) {
            file
        } else {
            // Файл удалён извне — запись больше не валидна
            downloadedTracksDao.delete(trackId)
            null
        }
    }

    override suspend fun save(track: Track, localFilePath: String) {
        withContext(Dispatchers.IO) {
            downloadedTracksDao.insert(track.toDownloadEntity(localFilePath))
        }
    }

    override suspend fun remove(trackId: String) {
        withContext(Dispatchers.IO) {
            val entry = downloadedTracksDao.getById(trackId)
            downloadedTracksDao.delete(trackId)
            entry?.localFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
    }
}
