package com.facet.music.data.model

data class AudioStreamResult(
    val url: String,
    val isHls: Boolean,
    val mimeType: String? = null,
    val expiresAtMs: Long? = null,
)
