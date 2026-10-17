package com.my.music.data.model

/**
 * Результат resolve аудио-потока.
 * Содержит URL и тип потока — чтобы ExoPlayer мог выбрать правильный MediaSource.
 *
 * @param url   прямой URL потока (MP3 или m3u8)
 * @param isHls true → HLS (m3u8, ExoPlayer использует HlsMediaSource)
 *              false → Progressive (прямой MP3, ProgressiveMediaSource)
 */
data class AudioStreamResult(
    val url: String,
    val isHls: Boolean,
)
