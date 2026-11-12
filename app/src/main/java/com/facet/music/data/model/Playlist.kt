package com.facet.music.data.model

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val coverUrl: String? = null,
)
