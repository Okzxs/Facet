package com.my.music.core.download

/** Тексты ошибок скачивания, одинаковые во всех экранах. */
object DownloadMessages {
    /**
     * Ни SoundCloud, ни YouTube не отдали progressive-файл.
     * Пользователь ещё может найти трек вручную через поиск.
     */
    const val SOURCE_NOT_FOUND =
        "Не удалось найти этот трек на YouTube для скачивания. " +
            "Возможно, он удален или доступен только в SoundCloud"
}
