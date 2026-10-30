package com.facet.music.core.download

/**
 * Возможность источника отдать один progressive-файл для скачивания.
 *
 * PROGRESSIVE — источник умеет вернуть цельный MP3/M4A/WEBM.
 * HLS_ONLY — только сегментированный поток, из него нельзя собрать файл.
 * NONE — скачивание не поддерживается.
 */
enum class DownloadCapability {
    PROGRESSIVE,
    HLS_ONLY,
    NONE,
}
