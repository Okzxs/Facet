package com.my.music.data.model

/**
 * Доменная модель трека — единая для всех источников.
 */
data class Track(
    val id: String,
    val source: Source,
    val title: String,
    val artist: String,
    /** URL обложки. Может быть null если SC не вернул artwork. */
    val artworkUrl: String?,
    /**
     * URL потока. Для SoundCloud — null до вызова resolve(),
     * потому что v2 API возвращает только ссылку на transcoding endpoint.
     */
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val liked: Boolean = false,
    /** Для SC: URL transcoding endpoint (требует дополнительного resolve) */
    val transcodingUrl: String? = null,
)

enum class Source { SOUNDCLOUD, SPOTIFY, YANDEX, VK, LOCAL }
