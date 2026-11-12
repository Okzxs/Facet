package com.facet.music.data.local

import android.net.Uri
import com.facet.music.data.local.dao.DownloadedTracksDao
import com.facet.music.data.local.entity.DownloadedTrackEntity
import com.facet.music.data.local.entity.toDomain
import com.facet.music.data.local.entity.toDownloadEntity
import com.facet.music.data.model.DownloadedTrack
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadedTracksDao: DownloadedTracksDao,
) : DownloadRepository {

    override fun observeDownloadedIds(): Flow<Set<String>> {
        return downloadedTracksDao.getAll()
            .map { list -> pruneUnplayable(list).map { it.id }.toSet() }
            .flowOn(Dispatchers.IO)
    }

    override fun observeIsDownloaded(trackId: String): Flow<Boolean> {
        return downloadedTracksDao.isDownloaded(trackId)
            .flowOn(Dispatchers.IO)
    }

    override fun observeDownloadedTracks(): Flow<List<DownloadedTrack>> {
        return downloadedTracksDao.getAll()
            .map { list -> pruneUnplayable(list).map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getDownloadedTrack(trackId: String): DownloadedTrack? =
        withContext(Dispatchers.IO) {
            val entry = downloadedTracksDao.getById(trackId) ?: return@withContext null
            if (!isPlayable(entry)) {
                downloadedTracksDao.delete(trackId)
                return@withContext null
            }
            entry.toDomain()
        }

    override suspend fun save(track: Track, mediaUrl: String, mimeType: String) {
        withContext(Dispatchers.IO) {
            downloadedTracksDao.insert(track.toDownloadEntity(mediaUrl, mimeType))
        }
    }

    override suspend fun remove(trackId: String) {
        withContext(Dispatchers.IO) {
            val entry = downloadedTracksDao.getById(trackId)
            downloadedTracksDao.delete(trackId)
            entry?.let { localFileOf(it)?.delete() }
        }
    }

    private fun isPlayable(entry: DownloadedTrackEntity): Boolean {
        val file = localFileOf(entry) ?: return false
        return file.exists() && file.length() > 0L
    }

    private suspend fun pruneUnplayable(
        entries: List<DownloadedTrackEntity>,
    ): List<DownloadedTrackEntity> {
        val (alive, dead) = entries.partition { isPlayable(it) }
        dead.forEach { downloadedTracksDao.delete(it.id) }
        return alive
    }

    private fun localFileOf(entry: DownloadedTrackEntity): File? {
        if (!entry.mediaUrl.startsWith("file://")) return null
        val path = Uri.parse(entry.mediaUrl).path ?: return null
        return File(path)
    }
}
