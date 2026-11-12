package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val query: String,
    val source: String,
    val jsonResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
)
