package com.facet.music.core.search

import com.facet.music.data.model.Track

object LocalSearch {

    fun match(tracks: List<Track>, query: String, limit: Int = 8): List<Track> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 2) return emptyList()
        val variants = buildList {
            add(normalized)
            if (Translit.hasCyrillic(normalized)) add(Translit.toLatin(normalized).lowercase())
            if (Translit.hasLatin(normalized)) add(Translit.toCyrillic(normalized).lowercase())
        }.distinct()

        return tracks
            .mapNotNull { track ->
                val title = track.title.lowercase()
                val artist = track.artist.lowercase()
                val haystack = "$title $artist"
                val score = variants.maxOfOrNull { scoreFor(haystack, title, artist, it) } ?: -1
                if (score < 0) null else track to score
            }
            .sortedWith(
                compareByDescending<Pair<Track, Int>> { it.second }
                    .thenBy { it.first.title.lowercase() },
            )
            .distinctBy { it.first.id }
            .take(limit)
            .map { it.first }
    }

    private fun scoreFor(haystack: String, title: String, artist: String, needle: String): Int {
        if (!haystack.contains(needle)) {
            val tokens = needle.split(' ').filter { it.isNotBlank() }
            if (tokens.isEmpty() || !tokens.all { haystack.contains(it) }) return -1
            return 10
        }
        return when {
            title.startsWith(needle) -> 100
            title.contains(needle) -> 80
            artist.startsWith(needle) -> 60
            else -> 40
        }
    }
}
