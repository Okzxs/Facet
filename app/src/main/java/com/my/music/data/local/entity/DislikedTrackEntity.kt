package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Трек, который пользователь отметил дизлайком.
 *
 * Храним только идентификатор и время: для фильтрации рекомендаций
 * остальных полей трека не нужно.
 */
@Entity(tableName = "disliked_tracks")
data class DislikedTrackEntity(
    @PrimaryKey
    val trackId: String,
    val dislikedAt: Long = System.currentTimeMillis(),
)
