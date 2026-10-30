package com.facet.music.core.download

/** Тексты ошибок скачивания, одинаковые во всех экранах. */
object DownloadMessages {
    /**
     * Ни SoundCloud, ни YouTube не отдали progressive-файл.
     * Пользователь ещё может найти трек вручную через поиск.
     */
    const val SOURCE_NOT_FOUND =
        "Не удалось найти этот трек на YouTube для скачивания. " +
            "Возможно, он удален или доступен только в SoundCloud"

    /**
     * Превращает сырое сообщение воркера в текст для Snackbar.
     * Служебные коды переводим в понятные фразы; null означает, что воркер
     * причину не передал и экран подставит свой текст по умолчанию.
     */
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
