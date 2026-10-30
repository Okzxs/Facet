package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Трек внутри плейлиста.
 *
 * Поля трека денормализованы: плейлист открывается без сети и без повторного
 * поиска в источнике. [playlistId] — внешний ключ с каскадным удалением:
 * удаление плейлиста само вычищает его строки, отдельный запрос не нужен.
 * Пара `(playlistId, trackId)` уникальна — один и тот же трек нельзя добавить
 * в плейлист дважды.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val source: String,
    val transcodingUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)
