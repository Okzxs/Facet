package com.facet.music.core.search

import com.facet.music.data.model.Artist
import java.text.Normalizer
import java.util.Locale

/** Нормализация имён и Jaro-Winkler для поиска артистов. */
object ArtistMerger {

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val NAME_SUFFIXES = listOf(" - topic", " official", " vevo")

    private val DIACRITICS = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o', 'ø' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ñ' to 'n', 'ç' to 'c', 'ý' to 'y', 'ÿ' to 'y',
    )

    fun normalize(name: String): String {
        var value = name.lowercase(Locale.ROOT).replace('ё', 'е')
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
        value = value.replace(MARKS, "")
        value = buildString(value.length) {
            value.forEach { char -> append(DIACRITICS[char] ?: char) }
        }
        NAME_SUFFIXES.forEach { suffix -> value = value.removeSuffix(suffix) }
        return NON_WORD.replace(value, " ").trim()
    }

    fun tokens(value: String): List<String> =
        normalize(value).split(' ').filter { it.isNotBlank() }

    /**
     * Слияние артистов по нормализованному имени. Аватар — сначала YouTube
     * Music, затем SoundCloud; verified и подписчики объединяются.
     */
    fun merge(groups: List<List<Artist>>, query: String): List<Artist> {
        val buckets = LinkedHashMap<String, MutableList<Artist>>()
        groups.forEach { artists ->
            artists.forEach { artist ->
                val key = normalize(artist.name)
                if (key.isNotBlank()) buckets.getOrPut(key) { mutableListOf() }.add(artist)
            }
        }
        val normalizedQuery = normalize(query)
        return buckets.values.map { candidates ->
            val ordered = candidates.sortedBy { if (it.source == com.facet.music.data.model.Source.YOUTUBE) 0 else 1 }
            val base = ordered.maxByOrNull { it.subscriberCount ?: 0L } ?: ordered.first()
            base.copy(
                avatarUrl = ordered.firstOrNull { it.avatarUrl != null }?.avatarUrl,
                subscriberCount = candidates.mapNotNull { it.subscriberCount }.maxOrNull(),
                isVerified = candidates.any { it.isVerified },
            )
        }.sortedWith(
            compareByDescending<Artist> { normalize(it.name) == normalizedQuery }
                .thenByDescending { it.isVerified }
                .thenByDescending { it.subscriberCount ?: 0L }
                .thenBy { normalize(it.name) },
        )
    }

    /** Лучший артист для «запроса-артиста»: точное имя, префикс или JW. */
    fun bestMatch(query: String, artists: List<Artist>): Artist? {
        val normalized = normalize(query)
        if (normalized.length < 2) return null
        return artists.maxByOrNull { artist ->
            val name = normalize(artist.name)
            when {
                name == normalized -> 1.0
                normalized.length >= SearchConfig.ARTIST_PREFIX_MIN && name.startsWith(normalized) -> 0.95
                jaroWinkler(normalized, name) >= SearchConfig.JARO_WINKLER_THRESHOLD ->
                    jaroWinkler(normalized, name)
                else -> 0.0
            }
        }?.takeIf { artist ->
            val name = normalize(artist.name)
            name == normalized ||
                (normalized.length >= SearchConfig.ARTIST_PREFIX_MIN && name.startsWith(normalized)) ||
                jaroWinkler(normalized, name) >= SearchConfig.JARO_WINKLER_THRESHOLD
        }
    }

    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val matchDistance = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0

        a.forEachIndexed { i, char ->
            val start = (i - matchDistance).coerceAtLeast(0)
            val end = (i + matchDistance).coerceAtMost(b.lastIndex)
            if (start > end) return@forEachIndexed
            for (j in start..end) {
                if (!bMatched[j] && b[j] == char) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        a.forEachIndexed { i, _ ->
            if (aMatched[i]) {
                while (!bMatched[k]) k++
                if (a[i] != b[k]) transpositions++
                k++
            }
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }
}
