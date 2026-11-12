package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.facet.music.data.model.DownloadedTrack
import com.facet.music.data.model.Track

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val mediaUrl: String,
    val mimeType: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val artistUrl: String? = null,
)

fun DownloadedTrackEntity.toDomain(): DownloadedTrack = DownloadedTrack(
    trackId = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    mediaUrl = mediaUrl,
    mimeType = mimeType,
    downloadedAt = downloadedAt,
    artistUrl = artistUrl,
)

fun Track.toDownloadEntity(
    mediaUrl: String,
    mimeType: String,
    downloadedAt: Long = System.currentTimeMillis(),
): DownloadedTrackEntity = DownloadedTrackEntity(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    mediaUrl = mediaUrl,
    mimeType = mimeType,
    downloadedAt = downloadedAt,
    artistUrl = artistUrl,
)
