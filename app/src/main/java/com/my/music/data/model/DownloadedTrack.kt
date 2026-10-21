package com.my.music.data.model

/**
 * Скачанный трек, доступный без интернета.
 *
 * @param trackId       идентификатор трека в исходном источнике (для SC — URL трека)
 * @param localFilePath абсолютный путь к файлу во внутреннем хранилище приложения
 * @param downloadedAt  время завершения скачивания (мс с начала эпохи)
 */
data class DownloadedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val localFilePath: String,
    val downloadedAt: Long,
)

/**
 * Превращает скачанный трек в доменную модель для плеера:
 * потоком выступает локальный файл.
 */
fun DownloadedTrack.toTrack(): Track = Track(
    id = trackId,
    source = Source.LOCAL,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    streamUrl = localFilePath,
    durationMs = durationMs,
    genre = null,
    liked = false,
    transcodingUrl = null,
)
