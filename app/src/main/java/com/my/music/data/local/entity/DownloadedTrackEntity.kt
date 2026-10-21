package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Track

/**
 * Сущность скачанного трека для таблицы downloaded_tracks в Room.
 * Обложка здесь только для UI — сам файл изображения не сохраняется.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val localFilePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
)

fun DownloadedTrackEntity.toDomain(): DownloadedTrack = DownloadedTrack(
    trackId = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    localFilePath = localFilePath,
    downloadedAt = downloadedAt,
)

fun Track.toDownloadEntity(
    localFilePath: String,
    downloadedAt: Long = System.currentTimeMillis(),
): DownloadedTrackEntity = DownloadedTrackEntity(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    localFilePath = localFilePath,
    downloadedAt = downloadedAt,
)
