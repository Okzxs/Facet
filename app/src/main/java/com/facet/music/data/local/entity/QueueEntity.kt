package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
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
