package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "genre_weights",
    indices = [Index("weight")],
)
data class GenreWeightEntity(
    @PrimaryKey
    val genre: String,
    val weight: Double,
    val updatedAt: Long,
)

@Entity(tableName = "artist_weights")
data class ArtistWeightEntity(
    @PrimaryKey
    val artist: String,
    val weight: Double,
    val updatedAt: Long,
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey
    val preferenceKey: String,
    val preferenceValue: String,
)
