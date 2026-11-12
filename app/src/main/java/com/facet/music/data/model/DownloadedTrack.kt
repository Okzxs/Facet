package com.facet.music.data.model

data class DownloadedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val mediaUrl: String,
    val mimeType: String,
    val downloadedAt: Long,
    val artistUrl: String? = null,
)

fun DownloadedTrack.toTrack(): Track = Track(
    id = trackId,
    source = Source.LOCAL,
    title = title,
    artist = artist,
    artistUrl = artistUrl,
    artworkUrl = artworkUrl,
    streamUrl = mediaUrl,
    durationMs = durationMs,
    genre = null,
    liked = false,
    transcodingUrl = null,
)
