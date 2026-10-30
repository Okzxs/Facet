package com.my.music.core.import

/** Сервис, ссылку на плейлист которого умеет разбирать приложение. */
enum class ImportService {
    SPOTIFY,
    YANDEX,
    APPLE,
    DEEZER,
}

/**
 * Причина, по которой импорт не удался.
 *
 * Текст для пользователя живёт в ресурсах (`import_error_*`), поэтому парсер
 * и загрузчик возвращают код, а не готовую строку: локаль выбирает UI.
 */
enum class ImportError {
    EMPTY_LINK,
    UNSUPPORTED_LINK,
    YANDEX_UNSUPPORTED,
    APPLE_PLAYLIST_UNSUPPORTED,
    SPOTIFY_NOT_CONFIGURED,
    EMPTY_PLAYLIST,
    NETWORK,
    SAVE_FAILED,
}

/**
 * Результат разбора ссылки: сервис и его идентификатор либо понятная ошибка.
 *
 * @param id идентификатор плейлиста (у Apple — сегмент `pl.…`);
 * @param trackId для Apple — числовой id трека из query-параметра `i=`,
 *   такую ссылку импортируем как один трек.
 */
sealed interface PlaylistLinkParseResult {
    data class Success(
        val service: ImportService,
        val id: String,
        val trackId: String? = null,
    ) : PlaylistLinkParseResult

    data class Failure(
        val error: ImportError,
        val message: String,
    ) : PlaylistLinkParseResult
}

/**
 * Разбор ссылок на плейлисты Spotify, Яндекс Музыки, Apple Music и Deezer.
 *
 * Разбор намеренно не использует `android.net.Uri` и `java.net.URI`: ссылки
 * часто копируют без схемы, с пробелами и кириллицей в пути (Apple Music),
 * а `URI` на таких строках падает. Поэтому путь и query-параметры разбираются
 * обычными строковыми операциями — это ещё и делает парсер чистым JVM-кодом.
 */
object PlaylistLinkParser {

    fun parse(raw: String): PlaylistLinkParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return failure(ImportError.EMPTY_LINK, "Вставьте ссылку на плейлист")
        }

        // Схему и userinfo отбрасываем: ссылку часто копируют без «https://».
        val withoutScheme = trimmed.replaceFirst(SCHEME_REGEX, "")
        val withoutFragment = withoutScheme.substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        val segments = path.split('/').filter { it.isNotBlank() }
        val host = segments.firstOrNull()
            ?.substringBefore(':')
            ?.lowercase()
            ?: return unsupported()

        // Приводим путь к виду без пустых элементов: [host, ...остальное].
        return when {
            isSpotifyHost(host) -> parseSpotify(segments.drop(1))
            isYandexHost(host) -> parseYandex(segments.drop(1))
            isAppleHost(host) -> parseApple(segments.drop(1), query)
            isDeezerHost(host) -> parseDeezer(segments.drop(1))
            else -> unsupported()
        }
    }

    /** `open.spotify.com/playlist/<id>`, `spotify.com/playlist/<id>`. */
    private fun parseSpotify(segments: List<String>): PlaylistLinkParseResult {
        val index = segments.indexOfFirst { it.equals(PLAYLIST_SEGMENT, ignoreCase = true) }
        if (index < 0) return unsupported()
        val id = segments.getOrNull(index + 1)
            ?.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() } }
            ?: return failure(
                ImportError.UNSUPPORTED_LINK,
                "В ссылке Spotify нет идентификатора плейлиста",
            )
        return PlaylistLinkParseResult.Success(ImportService.SPOTIFY, id)
    }

    /**
     * `music.yandex.ru/users/<user>/playlists/<id>` и
     * `music.yandex.ru/playlists/<id>`. У Яндекса сегмент во множественном числе.
     */
    private fun parseYandex(segments: List<String>): PlaylistLinkParseResult {
        val index = segments.indexOfFirst { it.equals(YANDEX_PLAYLISTS_SEGMENT, true) }
        if (index < 0) return unsupported()
        val id = segments.getOrNull(index + 1)?.takeIf { it.isNotEmpty() }
            ?: return failure(
                ImportError.UNSUPPORTED_LINK,
                "В ссылке Яндекс Музыки нет идентификатора плейлиста",
            )
        return PlaylistLinkParseResult.Success(ImportService.YANDEX, id)
    }

    /**
     * `music.apple.com/.../playlist/.../pl.<uuid>` — id плейлиста берём из
     * сегмента `pl.…`. Ссылка с query-параметром `i=` указывает на трек:
     * возвращаем ещё и его числовой id, чтобы импортировать один трек.
     */
    private fun parseApple(segments: List<String>, query: String): PlaylistLinkParseResult {
        val trackFromQuery = queryParam(query, TRACK_QUERY_PARAM)
            ?.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }
        val trackFromSongPath = segments.lastOrNull()
            ?.takeIf { it.all { char -> char.isDigit() } && segments.any { it.equals("song", true) } }

        val playlistSegment = segments.firstOrNull { it.startsWith(APPLE_PLAYLIST_PREFIX, true) }
        val trackId = trackFromQuery ?: trackFromSongPath
        if (trackId != null) {
            return PlaylistLinkParseResult.Success(
                service = ImportService.APPLE,
                // Ссылка вида «плейлист + i=» — это трек, но id плейлиста сохраняем.
                id = playlistSegment ?: trackId,
                trackId = trackId,
            )
        }

        if (playlistSegment != null) {
            return PlaylistLinkParseResult.Success(ImportService.APPLE, playlistSegment)
        }

        // Ссылка на альбом: iTunes умеет отдать его треки обычным lookup.
        val albumId = segments.lastOrNull()
            ?.takeIf { it.all { char -> char.isDigit() } }
        if (albumId != null && segments.any { it.equals("album", true) }) {
            return PlaylistLinkParseResult.Success(ImportService.APPLE, albumId)
        }

        return failure(
            ImportError.UNSUPPORTED_LINK,
            "Не удалось найти идентификатор в ссылке Apple Music",
        )
    }

    /** `deezer.com/playlist/<id>` и `deezer.com/ru/playlist/<id>`. */
    private fun parseDeezer(segments: List<String>): PlaylistLinkParseResult {
        val index = segments.indexOfFirst { it.equals(PLAYLIST_SEGMENT, ignoreCase = true) }
        if (index < 0) return unsupported()
        val id = segments.getOrNull(index + 1)
            ?.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }
            ?: return failure(
                ImportError.UNSUPPORTED_LINK,
                "В ссылке Deezer нет идентификатора плейлиста",
            )
        return PlaylistLinkParseResult.Success(ImportService.DEEZER, id)
    }

    private fun isSpotifyHost(host: String): Boolean =
        host == "spotify.com" || host.endsWith(".spotify.com")

    private fun isYandexHost(host: String): Boolean =
        host == "yandex.ru" || host.endsWith(".yandex.ru") ||
            host == "yandex.com" || host.endsWith(".yandex.com")

    private fun isAppleHost(host: String): Boolean =
        host == "music.apple.com" || host == "itunes.apple.com"

    private fun isDeezerHost(host: String): Boolean =
        host == "deezer.com" || host.endsWith(".deezer.com")

    /** Значение query-параметра по имени; null, если параметра нет. */
    private fun queryParam(query: String, name: String): String? =
        query.split('&')
            .firstOrNull { part -> part.substringBefore('=') == name }
            ?.substringAfter('=', missingDelimiterValue = "")

    private fun unsupported(): PlaylistLinkParseResult.Failure = failure(
        ImportError.UNSUPPORTED_LINK,
        "Не удалось распознать ссылку: поддержаны Spotify, Deezer, Яндекс Музыка и Apple Music",
    )

    private fun failure(error: ImportError, message: String): PlaylistLinkParseResult.Failure =
        PlaylistLinkParseResult.Failure(error = error, message = message)

    private const val PLAYLIST_SEGMENT = "playlist"
    private const val YANDEX_PLAYLISTS_SEGMENT = "playlists"
    private const val TRACK_QUERY_PARAM = "i"
    private const val APPLE_PLAYLIST_PREFIX = "pl."
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
