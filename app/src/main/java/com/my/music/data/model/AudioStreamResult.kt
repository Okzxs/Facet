package com.my.music.data.model

/**
 * Результат resolve аудио-потока.
 * Содержит URL, тип потока и MIME — чтобы ExoPlayer и загрузчик
 * одинаково понимали формат (MP3, M4A, WEBM или HLS).
 *
 * @param url      прямой URL потока (MP3/M4A/WEBM или m3u8)
 * @param isHls    true → HLS (m3u8, ExoPlayer использует HlsMediaSource)
 *                 false → Progressive (одиночный файл)
 * @param mimeType MIME, определённый источником; null — старый вызов без формата,
 *                 тогда плеер подставит безопасный фолбэк
 */
data class AudioStreamResult(
    val url: String,
    val isHls: Boolean,
    val mimeType: String? = null,
)
