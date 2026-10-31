package com.facet.music.core.ui.artwork

/**
 * Апгрейд обложек до максимального доступного размера.
 *
 * Источники отдают в списках миниатюры: SoundCloud `-large` (100×100),
 * YouTube `hqdefault` (480×360), Deezer `cover_big` (500×500), Apple
 * `100x100bb`. В плеере такие картинки растягиваются и выглядят мылом,
 * поэтому для полноэкранной обложки подставляем увеличенный вариант.
 *
 * Функция ничего не гарантирует: если формат URL незнаком, возвращается
 * исходная строка. Ошибку загрузки увеличенной версии вызывает уже UI
 * (fallback на оригинал).
 */
object ArtworkUrl {

    private val SOUNDCLOUD_SUFFIX = Regex(
        "-(large|t\\d+x\\d+|crop|small|tiny|mini|badge)\\.(jpg|jpeg|png|webp)",
        RegexOption.IGNORE_CASE,
    )
    private val YOUTUBE_THUMB = Regex(
        "/(hqdefault|mqdefault|sddefault|default)\\.(jpg|jpeg|webp)",
        RegexOption.IGNORE_CASE,
    )
    private val DEEZER_SIZE = Regex("/\\d+x\\d+-", RegexOption.IGNORE_CASE)
    private val APPLE_SIZE = Regex("\\d+x\\d+bb", RegexOption.IGNORE_CASE)

    /** Максимальная версия обложки или исходный URL, если источник незнаком. */
    fun highRes(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.contains("sndcdn.com") -> upgradeSoundCloud(value)
            value.contains("ytimg.com") || value.contains("youtube.com") -> upgradeYouTube(value)
            value.contains("dzcdn.net") -> DEEZER_SIZE.replace(value, "/1000x1000-")
            value.contains("mzstatic.com") -> APPLE_SIZE.replace(value, "600x600bb")
            else -> value
        }
    }

    private fun upgradeSoundCloud(url: String): String {
        // `-original` уже максимальный, его не трогаем.
        if (url.contains("-original.")) return url
        return SOUNDCLOUD_SUFFIX.replace(url) { match ->
            "-t500x500.${match.groupValues[2].lowercase()}"
        }
    }

    private fun upgradeYouTube(url: String): String {
        if (url.contains("maxresdefault.")) return url
        return YOUTUBE_THUMB.replace(url) { match ->
            "/maxresdefault.${match.groupValues[2].lowercase()}"
        }
    }
}
