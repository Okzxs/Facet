package com.my.music.data.repository

import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Track

interface SoundCloudRepository {
    /**
     * Поиск треков по запросу.
     * @param query строка поиска
     * @param limit максимальное кол-во результатов
     */
    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    /**
     * Получить аудио-поток для трека.
     * Возвращает [AudioStreamResult] с URL и признаком HLS.
     *
     * Progressive предпочтителен всегда. Для онлайн-плеера допустим HLS-фолбэк:
     * ExoPlayer играет m3u8 нативно. Для скачивания передавайте
     * [requireProgressive] = true — тогда HLS-треки завершатся ошибкой,
     * потому что из сегментов нельзя собрать один mp3-файл.
     */
    suspend fun resolveStreamUrl(
        transcodingUrl: String,
        requireProgressive: Boolean = false,
    ): Result<AudioStreamResult>
}
