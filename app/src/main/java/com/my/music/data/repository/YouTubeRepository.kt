package com.my.music.data.repository

import com.my.music.core.download.DownloadCapability
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Track

/**
 * Источник контента YouTube через NewPipe Extractor.
 *
 * Для прослушивания и скачивания используем только progressive-аудио:
 * прямые M4A (AAC) или WEBM_OPUS. HLS/DASH-сегменты не подходят,
 * потому что из них нельзя собрать один файл.
 */
interface YouTubeRepository {

    /** Способность источника отдать цельный файл для скачивания. */
    val downloadCapability: DownloadCapability

    /**
     * Поиск видео на YouTube.
     * @param query строка поиска
     * @param limit максимальное количество результатов
     */
    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    /**
     * Получить треки, похожие на видео [trackUrl].
     *
     * NewPipe отдаёт алгоритмические related-видео самого YouTube. Это
     * основной источник персональных рекомендаций: он находит исполнителей,
     * которых нет в лайках и истории пользователя.
     *
     * @param trackUrl URL страницы видео
     * @param limit максимальное количество related-треков
     */
    suspend fun getRelated(trackUrl: String, limit: Int = 30): Result<List<Track>>

    /**
     * Получить прямой progressive-аудиопоток по URL видео.
     * Возвращает [AudioStreamResult] с `isHls = false` либо ошибку,
     * если progressive-аудио недоступно.
     */
    suspend fun resolveStreamUrl(videoUrl: String): Result<AudioStreamResult>
}
