package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Накопленный вес жанра по feedback-событиям волны.
 */
@Entity(tableName = "genre_weights")
data class GenreWeightEntity(
    @PrimaryKey
    val genre: String,
    val weight: Double,
    val updatedAt: Long,
)

/**
 * Накопленный вес артиста по feedback-событиям волны.
 */
@Entity(tableName = "artist_weights")
data class ArtistWeightEntity(
    @PrimaryKey
    val artist: String,
    val weight: Double,
    val updatedAt: Long,
)

/**
 * Простые пользовательские предпочтения волны (например, pref_ru_language).
 */
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey
    val preferenceKey: String,
    val preferenceValue: String,
)
