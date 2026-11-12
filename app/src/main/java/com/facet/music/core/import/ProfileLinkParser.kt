package com.facet.music.core.import

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

    private fun parseSpotify(segments: List<String>): ParsedProfile? {
        val index = segments.indexOfFirst { it.equals("user", ignoreCase = true) }
        if (index < 0) return null
        val id = segments.getOrNull(index + 1)?.takeIf { it.isNotEmpty() } ?: return null
        return ParsedProfile(ImportService.SPOTIFY, id)
    }

    private fun parseSoundCloud(host: String, segments: List<String>): ParsedProfile? {
        if (segments.isEmpty()) return null
        val user = segments.first()
        if (user.equals("you", true) || user.equals("discover", true)) return null
        val isProfileTab = segments.size == 1 ||
            (segments.size == 2 && segments[1].lowercase() in PROFILE_TABS)
        if (!isProfileTab) return null
        val canonicalHost = host.removePrefix("www.").removePrefix("m.")
        return ParsedProfile(ImportService.SOUNDCLOUD, "https://$canonicalHost/$user")
    }

    private fun isSpotifyHost(host: String): Boolean =
        host == "spotify.com" || host.endsWith(".spotify.com")

    private fun isSoundCloudHost(host: String): Boolean =
        host == "soundcloud.com" || host.endsWith(".soundcloud.com")

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    private val PROFILE_TABS = setOf("likes", "tracks", "albums", "playlists", "reposts")
}
