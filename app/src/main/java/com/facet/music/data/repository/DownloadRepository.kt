package com.facet.music.data.repository

import com.facet.music.data.model.DownloadedTrack
import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    fun observeDownloadedIds(): Flow<Set<String>>

    fun observeIsDownloaded(trackId: String): Flow<Boolean>

    fun observeDownloadedTracks(): Flow<List<DownloadedTrack>>

    suspend fun getDownloadedTrack(trackId: String): DownloadedTrack?

    suspend fun save(track: Track, mediaUrl: String, mimeType: String)

    suspend fun remove(trackId: String)
}
