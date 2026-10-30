package com.facet.music.data.model

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
    /** ISRC из метаданных Deezer — ключ для точного сопоставления между источниками. */
    val isrc: String? = null,
    /**
     * Длина превью в секундах (Deezer/iTunes отдают только 30-секундный отрывок).
     * null — трек играет целиком.
     */
    val previewSeconds: Int? = null,
    /**
     * Сколько источников нашли этот трек при сводном поиске (1 — только один).
     * В обычной выдаче всегда 1, бейдж «+N» показывается при N > 1.
     */
    val sourceCount: Int = 1,
)

enum class Source { SOUNDCLOUD, YOUTUBE, SPOTIFY, YANDEX, VK, DEEZER, APPLE, LOCAL }
