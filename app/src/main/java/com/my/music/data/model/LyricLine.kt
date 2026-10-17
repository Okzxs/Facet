package com.my.music.data.model

import kotlinx.serialization.Serializable

/**
 * Строка текста песни.
 * @param timeMs временная отметка в миллисекундах (-1 для несинхронизированного текста)
 * @param text текст строки
 */
@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Результат парсинга текстов песен.
 */
data class LyricsResult(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val isInstrumental: Boolean = false,
    val provider: String = "LRCLIB",
)
