package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Плейлист пользователя.
 *
 * Имя и дата создания живут отдельно от треков: переименование не переписывает
 * сотни строк `playlist_tracks`. Порядок в списке задаёт [createdAt], поэтому
 * новый плейлист оказывается ниже ранее созданных.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
