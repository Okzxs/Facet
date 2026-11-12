package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolved_matches")
data class ResolvedMatchEntity(
    @PrimaryKey val originalTrackId: String,
    val matchedSource: String,
    val matchedUrl: String,
    val confidence: Double,
    val createdAt: Long,
)
