package com.facet.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val source: Source,
    val title: String,
    val artist: String,
    val artistUrl: String? = null,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val liked: Boolean = false,
    val transcodingUrl: String? = null,
    val externalId: String? = null,
    val isSubscriptionOnly: Boolean = false,
    val isrc: String? = null,
    val previewSeconds: Int? = null,
    val sourceCount: Int = 1,
    val playCount: Long = 0L,
)

@Serializable
enum class Source { SOUNDCLOUD, YOUTUBE, SPOTIFY, YANDEX, VK, DEEZER, APPLE, LOCAL }

val Source.isMetadataOnly: Boolean
    get() = this == Source.DEEZER || this == Source.APPLE || this == Source.SPOTIFY
