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
     * Длина превью в секундах. Поле осталось только для чтения старых строк БД:
     * новые треки превью не содержат и всегда играют полную версию.
     */
    val previewSeconds: Int? = null,
    /**
     * Сколько источников нашли этот трек при сводном поиске (1 — только один).
     * В обычной выдаче всегда 1, бейдж «+N» показывается при N > 1.
     */
    val sourceCount: Int = 1,
)

enum class Source { SOUNDCLOUD, YOUTUBE, SPOTIFY, YANDEX, VK, DEEZER, APPLE, LOCAL }

/**
 * У источника нет собственного полного аудиопотока: Deezer и Apple отдают
 * только метаданные, Spotify закрыт DRM. Такие треки всегда играются через
 * YouTube-матч, а их `streamUrl` (превью из старых данных) игнорируется.
 *
 * Значения [Source.DEEZER] и [Source.APPLE] остаются в enum только для чтения
 * старых строк БД.
 */
val Source.isMetadataOnly: Boolean
    get() = this == Source.DEEZER || this == Source.APPLE || this == Source.SPOTIFY
