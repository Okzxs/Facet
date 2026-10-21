package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Track

/**
 * Сущность скачанного трека для таблицы downloaded_tracks в Room.
 *
 * Для HLS-трека нет одного локального файла: Media3 складывает сегменты
 * в SimpleCache, поэтому храним ссылку на поток и его MIME-тип.
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
    /** URL потока: m3u8-плейлист или прямой MP3. */
    val mediaUrl: String,
    /** MimeTypes.APPLICATION_M3U8 для HLS либо MimeTypes.AUDIO_MPEG. */
    val mimeType: String,
    val downloadedAt: Long = System.currentTimeMillis(),
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
)
