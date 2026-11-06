package com.facet.music.core.text

import java.util.Locale

/**
 * Единая нормализация музыкальных подписей: артист, название, ключ трека,
 * жанровые токены и маркеры версий. Нужна и рекомендациям, и «Моей волне»,
 * чтобы один трек из разных источников давал один ключ.
 */
object MusicNormalizer {

    fun normalizeArtist(raw: String): String {
        if (raw.isBlank()) return ""
        return stripArtistCredits(raw)
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /** Отображаемое имя без feat./prod. и лишних пробелов, с исходным регистром. */
    fun stripArtistCredits(raw: String): String = raw.replace(ARTIST_CREDITS_PATTERN, "")

    /** Нормализованное название без скобочных пометок и пунктуации. */
    fun normalizeTitle(raw: String): String = raw
        .lowercase(Locale.ROOT)
        .replace(TITLE_NOISE, " ")
        .replace(NON_WORD, " ")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    /**
     * Ключ сравнения: нормализованный артист и название. Пустое название
     * даёт пустой ключ — такие строки нельзя схлопывать между собой.
     */
    fun trackKey(artist: String, title: String): String {
        val titleKey = normalizeTitle(title)
        if (titleKey.isBlank()) return ""
        return "${normalizeArtist(artist)}|$titleKey"
    }

    fun normalizeWords(value: String): String =
        NON_WORD.replace(value.lowercase(Locale.ROOT), " ").trim()

    /** Жанр в виде набора значимых слов; пустой набор — жанра нет. */
    fun genreTokens(value: String?): Set<String> =
        normalizeWords(value.orEmpty())
            .split(' ')
            .asSequence()
            .filter { it.length >= MIN_GENRE_TOKEN_LENGTH }
            .toSet()

    /** Маркеры «неправильной» версии, найденные в названии. */
    fun markersIn(value: String): Set<String> {
        if (value.isBlank()) return emptySet()
        val normalized = " ${normalizeWords(value)} "
        return MARKERS
            .filter { (_, pattern) -> pattern.containsMatchIn(normalized) }
            .keys
    }

    private const val MIN_GENRE_TOKEN_LENGTH = 3

    private val WHITESPACE_PATTERN = Regex("\\s+")

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Скобочные пометки версии: «(Official Video)», «[Remastered]». */
    private val TITLE_NOISE = Regex("[(\\[][^)\\]]*[)\\]]")

    private val ARTIST_CREDITS_PATTERN = Regex(
        pattern = """(?:^|[\s(\[]+)(?:feat|ft|featuring|prod|produced)\b\.?\s*(?:by\s+)?.*$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val MARKERS: Map<String, Regex> = linkedMapOf(
        "full album" to Regex("\\bfull\\s+albums?\\b"),
        "1 hour" to Regex("\\b(?:1|one)\\s+hours?\\b"),
        "mix 20xx" to Regex("\\bmix\\s+20\\d{2}\\b"),
        "8d" to Regex("\\b8d\\b"),
        "nightcore" to Regex("\\bnightcore\\b"),
        "sped up" to Regex("\\bsped\\s+up\\b|\\bspeed\\s+up\\b"),
        "slowed" to Regex("\\bslowed\\b"),
        "karaoke" to Regex("\\bkaraoke\\b|\\bкараоке\\b"),
        "instrumental" to Regex("\\binstrumental\\b|\\bминус\\b"),
        "cover" to Regex("\\bcover\\b|\\bкавер\\b"),
    )
}
