package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Трек, который уже попал в ленту рекомендаций.
 * Используется, чтобы Pull-to-refresh не показывал одни и те же треки.
 */
@Entity(
    tableName = "recently_shown",
    indices = [Index(value = ["shownAt"])],
)
data class RecentlyShownEntity(
    @PrimaryKey
    val trackId: String,
    val shownAt: Long,
)
