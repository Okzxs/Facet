package com.facet.music.data.local.entity

import androidx.room.Entity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track

/**
 * Сущность кэша чартов SoundCloud ("New & hot", "Top 10") в Room.
 */
@Entity(
    tableName = "chart_cache",
    primaryKeys = ["chartType", "id"],
)
data class ChartCacheEntity(
    val chartType: String,
    val id: String,
    val position: Int,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val transcodingUrl: String?,
    val cachedAt: Long = System.currentTimeMillis(),
    val artistUrl: String? = null,
)

fun ChartCacheEntity.toTrack(): Track = Track(
    id = id,
    source = Source.SOUNDCLOUD,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artworkUrl = artworkUrl,
    streamUrl = null,
    durationMs = durationMs,
    genre = genre,
    liked = false,
    transcodingUrl = transcodingUrl,
)

fun Track.toChartEntity(
    chartType: String,
    position: Int,
    cachedAt: Long = System.currentTimeMillis(),
): ChartCacheEntity = ChartCacheEntity(
    chartType = chartType,
    id = id,
    position = position,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    genre = genre,
    transcodingUrl = transcodingUrl,
    cachedAt = cachedAt,
    artistUrl = artistUrl,
)
