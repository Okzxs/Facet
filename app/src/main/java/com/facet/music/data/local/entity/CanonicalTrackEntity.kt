package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canonical_tracks")
data class CanonicalTrackEntity(
    @PrimaryKey val canonicalKey: String,
    val title: String,
    val artist: String,
    val primaryTrackId: String,
    val updatedAt: Long,
)
