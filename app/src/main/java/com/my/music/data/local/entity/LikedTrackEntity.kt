package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.my.music.data.model.SeasonMemory
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
    /** Широта на момент лайка (null, если нет разрешения или геопозиция недоступна). */
    val latitude: Double? = null,
    /** Долгота на момент лайка. */
    val longitude: Double? = null,
    /** Сезон лайка: Winter / Spring / Summer / Autumn. */
    val season: String? = null,
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

fun LikedTrackEntity.toSeasonMemory(): SeasonMemory = SeasonMemory(
    track = toTrack(),
    season = season,
    latitude = latitude,
    longitude = longitude,
    likedAt = likedAt,
)

fun Track.toLikedEntity(
    likedAt: Long = System.currentTimeMillis(),
    latitude: Double? = null,
    longitude: Double? = null,
    season: String? = null,
): LikedTrackEntity = LikedTrackEntity(
    id = id,
    source = source.name,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    genre = genre,
    transcodingUrl = transcodingUrl,
    likedAt = likedAt,
    latitude = latitude,
    longitude = longitude,
    season = season,
)
