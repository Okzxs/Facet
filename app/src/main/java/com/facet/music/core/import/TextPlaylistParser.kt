package com.facet.music.core.import

data class TextTrackQuery(
    val artist: String?,
    val title: String,
    val raw: String,
)

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
