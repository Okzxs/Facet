package com.my.music.data.model

/**
 * Скачанный трек, доступный без интернета.
 *
 * @param trackId      идентификатор трека в исходном источнике (для SC — URL трека)
 * @param mediaUrl     локальный `file://` URL mp3-файла
 * @param mimeType     MIME-тип файла (для mp3 — AUDIO_MPEG)
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
 * [mediaUrl] указывает на локальный файл, поэтому сеть не нужна.
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
