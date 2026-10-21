package com.my.music.core.download

/**
 * Состояние загрузки по данным Media3 DownloadManager.
 * Отличается от [DownloadState]: здесь нет записи в Room, только сам кэш.
 */
sealed interface MediaDownloadState {

    /** Загрузки нет в индексе Media3. */
    data object Missing : MediaDownloadState

    /** Задача поставлена в очередь, но ещё не начала качаться. */
    data object Queued : MediaDownloadState

    /** Идёт загрузка, [progress] — доля от 0f до 1f. */
    data class InProgress(val progress: Float) : MediaDownloadState

    /** Все сегменты (или MP3 целиком) лежат в SimpleCache. */
    data object Completed : MediaDownloadState

    /** Загрузка завершилась ошибкой. */
    data class Failed(val reason: String?) : MediaDownloadState
}
