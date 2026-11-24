package com.facet.music.core.import

data class TakeoutLikedTrack(
    val videoId: String,
    val title: String,
    val artist: String,
)

object YouTubeTakeoutParser {

    fun parse(csv: String): List<TakeoutLikedTrack> {
        val rows = readRows(csv)
        if (rows.isEmpty()) return emptyList()
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.contains("video id", ignoreCase = true) } &&
                row.any { it.contains("title", ignoreCase = true) }
        }
        val header = rows.getOrNull(headerIndex) ?: return emptyList()
        val idIndex = header.indexOfFirst { it.contains("video id", ignoreCase = true) }
        val titleIndex = header.indexOfFirst { it.equals("title", ignoreCase = true) }
        if (idIndex < 0 || titleIndex < 0) return emptyList()
        val result = LinkedHashMap<String, TakeoutLikedTrack>()
        rows.drop(headerIndex + 1).forEach { row ->
            if (row.size <= maxOf(idIndex, titleIndex)) return@forEach
            val videoId = row[idIndex].trim()
            val rawTitle = row[titleIndex].trim()
            if (videoId.isBlank() || rawTitle.isBlank()) return@forEach
            val (artist, title) = splitTitle(rawTitle)
            result.putIfAbsent(videoId, TakeoutLikedTrack(videoId, title, artist))
        }
        return result.values.toList()
    }

    private fun splitTitle(raw: String): Pair<String, String> {
        val separator = raw.indexOf(" - ")
        if (separator <= 0) return "" to raw
        val artist = raw.substring(0, separator).trim()
        val title = raw.substring(separator + 3).trim()
        return artist to title.ifBlank { raw }
    }

    private fun readRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                char == '"' && quoted && csv.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && csv.getOrNull(index + 1) == '\n') index++
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotBlank() }) rows += row
        }
        return rows
    }
}
