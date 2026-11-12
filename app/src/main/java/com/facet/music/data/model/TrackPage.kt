package com.facet.music.data.model

data class TrackPage(
    val tracks: List<Track>,
    val nextToken: String? = null,
)
