package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Очередь воспроизведения в Room: одна строка — один трек.
 * [position] — порядковый номер, он же первичный ключ: очередь перезаписывается
 * целиком, поэтому отдельный id не нужен.
 */
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
    /** Источник строкой: enum переживает обфускацию только по имени. */
    val source: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val transcodingUrl: String?,
    val isrc: String?,
    val previewSeconds: Int?,
    val artistUrl: String? = null,
)
