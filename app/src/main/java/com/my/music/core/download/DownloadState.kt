package com.my.music.core.download

/**
 * Состояние скачивания трека для UI.
 */
sealed interface DownloadState {

    /** Трек не скачан (или скачивание отменено/провалилось). */
    data object NotDownloaded : DownloadState

    /** Идёт скачивание, [progress] — доля от 0f до 1f. */
    data class InProgress(val progress: Float) : DownloadState

    /** Трек скачан и доступен офлайн. */
    data object Downloaded : DownloadState

    /** Скачивание завершилось ошибкой — можно повторить по нажатию. */
    data object Failed : DownloadState
}
