package com.facet.music.data.model

data class SeasonMemory(
    val track: Track,
    val season: String?,
    val latitude: Double?,
    val longitude: Double?,
    val likedAt: Long,
)
