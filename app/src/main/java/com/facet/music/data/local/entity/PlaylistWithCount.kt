package com.facet.music.data.local.entity

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val coverUrl: String? = null,
)
