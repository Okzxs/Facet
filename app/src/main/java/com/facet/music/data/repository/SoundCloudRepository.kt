package com.facet.music.data.repository

import com.facet.music.core.download.DownloadCapability
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Track

interface SoundCloudRepository {

    /** Способность источника отдать цельный файл для скачивания. */
    val downloadCapability: DownloadCapability

    /**
     * Поиск треков по запросу.
     * @param query строка поиска
     * @param limit максимальное кол-во результатов
     */
    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    /**
     * Получить треки, похожие на трек [trackUrl].
     *
     * Related-выдача SoundCloud даёт исполнителей того же жанра и аудитории,
     * включая нишевые проекты, которых нет в персональной истории.
     *
     * @param trackUrl URL страницы трека
     * @param limit максимальное количество related-треков
     */
    suspend fun getRelated(trackUrl: String, limit: Int = 30): Result<List<Track>>

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
