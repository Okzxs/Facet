package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Найденный внешний матч metadata-only трека. Повторный запуск не ищет
 * заново: из [matchedUrl] снова получается свежий поток.
 */
@Entity(tableName = "resolved_matches")
data class ResolvedMatchEntity(
    @PrimaryKey val originalTrackId: String,
    val matchedSource: String,
    val matchedUrl: String,
    val confidence: Double,
    val createdAt: Long,
)
