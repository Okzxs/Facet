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
     * URL потока. Для SoundCloud и YouTube — null до вызова resolve():
     * источник возвращает только ссылку на страницу трека/видео.
     */
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val liked: Boolean = false,
    /** URL страницы трека/видео для последующего resolve потока. */
    val transcodingUrl: String? = null,
)

enum class Source { SOUNDCLOUD, YOUTUBE, SPOTIFY, YANDEX, VK, LOCAL }
