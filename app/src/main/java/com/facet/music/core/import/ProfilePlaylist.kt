package com.facet.music.core.import

data class ProfilePlaylist(
    val service: ImportService,
    val id: String,
    val url: String?,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
)
