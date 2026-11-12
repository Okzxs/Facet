package com.facet.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val source: Source,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long? = null,
    val isVerified: Boolean = false,
    val url: String,
)
