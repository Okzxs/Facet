package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.my.music.data.model.Source
import com.my.music.data.model.Track

/**
 * Сущность лайкнутого трека для таблицы liked_tracks в Room.
 */
@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val transcodingUrl: String?,
    val likedAt: Long = System.currentTimeMillis(),
)

fun LikedTrackEntity.toTrack(): Track = Track(
    id = id,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.SOUNDCLOUD),
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    streamUrl = null,
    durationMs = durationMs,
    genre = genre,
    liked = true,
    transcodingUrl = transcodingUrl,
)

fun Track.toLikedEntity(likedAt: Long = System.currentTimeMillis()): LikedTrackEntity = LikedTrackEntity(
    id = id,
    source = source.name,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    genre = genre,
    transcodingUrl = transcodingUrl,
    likedAt = likedAt,
)
