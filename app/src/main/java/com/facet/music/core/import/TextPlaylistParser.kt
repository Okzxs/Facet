package com.facet.music.core.import

/**
 * Строка списка треков: либо «артист - название», либо просто название.
 * [raw] — исходная строка (пригодится для отчёта о ненайденных).
 */
data class TextTrackQuery(
    val artist: String?,
    val title: String,
    val raw: String,
)

/**
 * Разбор списка треков из текста: каждая непустая строка — трек.
 *
 * Разделитель — ровно « - » (пробел, дефис, пробел): так SoundCloud и
 * большинство экспортов пишут «Artist - Title». Если разделителя нет,
 * вся строка уходит в поиск как есть. Пустые строки игнорируются,
 * повторы по названию схлопываются.
 */
object TextPlaylistParser {

    private const val SEPARATOR = " - "

    fun parse(text: String): List<TextTrackQuery> {
        val result = LinkedHashMap<String, TextTrackQuery>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val separatorIndex = trimmed.indexOf(SEPARATOR)
            val query = if (separatorIndex > 0) {
                val artist = trimmed.substring(0, separatorIndex).trim()
                val title = trimmed.substring(separatorIndex + SEPARATOR.length).trim()
                if (title.isBlank()) return@forEach
                TextTrackQuery(
                    artist = artist.takeIf { it.isNotBlank() },
                    title = title,
                    raw = trimmed,
                )
            } else {
                TextTrackQuery(artist = null, title = trimmed, raw = trimmed)
            }
            result.putIfAbsent(query.raw.lowercase(), query)
        }
        return result.values.toList()
    }
}
