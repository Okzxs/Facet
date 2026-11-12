package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disliked_tracks")
data class DislikedTrackEntity(
    @PrimaryKey
    val trackId: String,
    val dislikedAt: Long = System.currentTimeMillis(),
)
