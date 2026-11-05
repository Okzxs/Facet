package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track

/**
 * Сущность истории прослушиваний для таблицы play_history в Room.
 * Индекс по playedAt: недавние прослушивания выбираются по времени.
 */
@Entity(
    tableName = "play_history",
    indices = [Index("playedAt")],
)
data class PlayHistoryEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val transcodingUrl: String?,
    val playedAt: Long = System.currentTimeMillis(),
    val artistUrl: String? = null,
)

fun PlayHistoryEntity.toTrack(isLiked: Boolean = false): Track = Track(
    id = id,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.SOUNDCLOUD),
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artworkUrl = artworkUrl,
    streamUrl = null,
    durationMs = durationMs,
    genre = genre,
    liked = isLiked,
    transcodingUrl = transcodingUrl,
)

fun Track.toHistoryEntity(playedAt: Long = System.currentTimeMillis()): PlayHistoryEntity = PlayHistoryEntity(
    id = id,
    source = source.name,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    genre = genre,
    transcodingUrl = transcodingUrl,
    playedAt = playedAt,
)
