package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track

@Entity(
    tableName = "liked_tracks",
    indices = [Index("season"), Index("likedAt")],
)
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val season: String? = null,
    val artistUrl: String? = null,
)

fun LikedTrackEntity.toTrack(): Track = Track(
    id = id,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.SOUNDCLOUD),
    title = title,
    artist = artist,
    artistUrl = artistUrl,
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
    artistUrl = artistUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    genre = genre,
    transcodingUrl = transcodingUrl,
    likedAt = likedAt,
    latitude = latitude,
    longitude = longitude,
    season = season,
)
