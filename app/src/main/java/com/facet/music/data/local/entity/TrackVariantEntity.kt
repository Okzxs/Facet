package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "track_variants",
    primaryKeys = ["canonicalKey", "trackId"],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["lastCheckedAt"]),
    ],
)
data class TrackVariantEntity(
    val canonicalKey: String,
    val trackId: String,
    val source: String,
    val title: String,
    val artist: String,
    val sourceUrl: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val confidence: Double,
    val lastCheckedAt: Long,
    val trackJson: String,
)
