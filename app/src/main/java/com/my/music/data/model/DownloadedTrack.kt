package com.my.music.data.model

/**
 * Скачанный трек, доступный без интернета.
 *
 * @param trackId      идентификатор трека в исходном источнике (для SC — URL трека)
 * @param mediaUrl     URL потока, который лежит в кэше Media3 (m3u8 или MP3)
 * @param mimeType     MIME-тип потока: MimeTypes.APPLICATION_M3U8 или AUDIO_MPEG
 * @param downloadedAt время завершения скачивания (мс с начала эпохи)
 */
data class DownloadedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val mediaUrl: String,
    val mimeType: String,
    val downloadedAt: Long,
)

/**
 * Превращает скачанный трек в доменную модель для плеера.
 * Поток отдаёт тот же URL: плеер найдёт его в общем SimpleCache.
 */
fun DownloadedTrack.toTrack(): Track = Track(
    id = trackId,
    source = Source.LOCAL,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    streamUrl = mediaUrl,
    durationMs = durationMs,
    genre = null,
    liked = false,
    transcodingUrl = null,
)
