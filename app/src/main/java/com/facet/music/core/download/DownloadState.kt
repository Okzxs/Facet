package com.facet.music.core.download

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

    /**
     * Ошибка или отмена задачи.
     * UI показывает крестик, затем через секунду возвращает иконку скачивания.
     *
     * @param message текст из `outputData` воркера, если он его передал;
     * по нему Snackbar объясняет причину вместо безликого «не скачалось».
     */
    data class Error(
        val cause: Cause,
        val message: String? = null,
    ) : DownloadState

    /** Причина крестика: отмена пользователем или реальный провал загрузки. */
    enum class Cause {
        CANCELLED,
        FAILED,
    }
}
