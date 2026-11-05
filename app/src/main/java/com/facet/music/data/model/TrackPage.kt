package com.facet.music.data.model

/** Страница выдачи источника: токен непрозрачен для ViewModel. */
data class TrackPage(
    val tracks: List<Track>,
    val nextToken: String? = null,
)
