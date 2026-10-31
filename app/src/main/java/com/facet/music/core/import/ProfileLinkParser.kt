package com.facet.music.core.import

/**
 * Разбор ссылки на публичный профиль Spotify или SoundCloud.
 *
 * Возвращает сервис и идентификатор: для Spotify — user id, для SoundCloud —
 * канонический URL профиля. Ссылки на треки/плейлисты сюда не подходят.
 */
object ProfileLinkParser {

    data class ParsedProfile(
        val service: ImportService,
        val id: String,
    )

    fun parse(raw: String): ParsedProfile? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withoutScheme = trimmed.replaceFirst(SCHEME_REGEX, "")
        val withoutFragment = withoutScheme.substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        val segments = path.split('/').filter { it.isNotBlank() }
        val host = segments.firstOrNull()?.substringBefore(':')?.lowercase() ?: return null
        val tail = segments.drop(1)

        return when {
            isSpotifyHost(host) -> parseSpotify(tail)
            isSoundCloudHost(host) -> parseSoundCloud(host, tail)
            else -> null
        }
    }

    /** `open.spotify.com/user/<id>` — id идёт сразу после сегмента `user`. */
    private fun parseSpotify(segments: List<String>): ParsedProfile? {
        val index = segments.indexOfFirst { it.equals("user", ignoreCase = true) }
        if (index < 0) return null
        val id = segments.getOrNull(index + 1)?.takeIf { it.isNotEmpty() } ?: return null
        return ParsedProfile(ImportService.SPOTIFY, id)
    }

    /** `soundcloud.com/<user>` — профиль, а не трек и не сет. */
    private fun parseSoundCloud(host: String, segments: List<String>): ParsedProfile? {
        if (segments.size != 1) return null
        val user = segments.single()
        if (user.equals("you", true) || user.equals("discover", true)) return null
        return ParsedProfile(ImportService.SOUNDCLOUD, "https://$host/$user")
    }

    private fun isSpotifyHost(host: String): Boolean =
        host == "spotify.com" || host.endsWith(".spotify.com")

    private fun isSoundCloudHost(host: String): Boolean =
        host == "soundcloud.com" || host.endsWith(".soundcloud.com")

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
