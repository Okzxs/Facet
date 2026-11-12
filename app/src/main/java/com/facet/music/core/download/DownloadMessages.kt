package com.facet.music.core.download

object DownloadMessages {
    const val SOURCE_NOT_FOUND =
        "Не удалось найти этот трек на YouTube для скачивания. " +
            "Возможно, он удален или доступен только в SoundCloud"

    fun friendly(raw: String?): String? = when (raw) {
        null, ERROR_UNKNOWN -> null
        ERROR_EMPTY_FILE -> "Сервер отдал пустой файл"
        ERROR_DOWNLOADS_DIR -> "Не удалось создать папку загрузок"
        else -> raw
    }

    private const val ERROR_UNKNOWN = DownloadTrackWorker.ERROR_UNKNOWN
    private const val ERROR_EMPTY_FILE = "empty_file"
    private const val ERROR_DOWNLOADS_DIR = "downloads_dir_unavailable"
}
