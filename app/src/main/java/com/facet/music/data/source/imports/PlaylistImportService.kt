package com.facet.music.data.source.imports

import android.util.Log
import com.facet.music.core.import.ImportError
import com.facet.music.core.import.ImportService
import com.facet.music.core.import.ImportedPlaylist
import com.facet.music.core.import.PlaylistLinkParseResult
import com.facet.music.core.import.PlaylistLinkParser
import com.facet.music.core.import.PlaylistPayloadParser
import com.facet.music.core.import.ProfileLinkParser
import com.facet.music.core.import.ProfilePlaylist
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.source.external.SpotifyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат загрузки плейлиста по ссылке.
 *
 * Ошибка несёт [ImportError] — UI сам выбирает локализованный текст
 * (`import_error_*`), а [message] остаётся для лога и отладки.
 */
sealed interface PlaylistImportResult {
    data class Success(val playlist: ImportedPlaylist) : PlaylistImportResult

    data class Failure(
        val error: ImportError,
        val message: String,
    ) : PlaylistImportResult
}

/**
 * Результат загрузки публичного профиля: список плейлистов для выбора.
 */
sealed interface ProfileImportResult {
    data class Success(val playlists: List<ProfilePlaylist>) : ProfileImportResult
    data class Failure(val error: ImportError, val message: String) : ProfileImportResult
}

/**
 * Загрузчик плейлиста по ссылке.
 *
 * Разбирает ссылку, ходит в сеть и возвращает треки — но ничего не пишет
 * в Room: сохранение и создание плейлиста делает вызывающая сторона через
 * [com.facet.music.data.repository.PlaylistRepository]. Так превью можно
 * показать до подтверждения импорта.
 */
interface PlaylistImportService {

    /** Загружает плейлист по ссылке. Отмену корутины пробрасывает дальше. */
    suspend fun load(link: String): PlaylistImportResult

    /**
     * Публичные плейлисты профиля Spotify или SoundCloud — для экрана
     * «Импорт профиля». Ссылки на треки и плейлисты не принимаются.
     */
    suspend fun loadProfile(link: String): ProfileImportResult
}

/**
 * Spotify — Client Credentials через [SpotifyProvider]; Deezer и iTunes —
 * открытые API без авторизации.
 *
 * У Deezer и iTunes ответы разбирает [PlaylistPayloadParser] (чистые JVM-функции),
 * а сеть идёт через общий [OkHttpClient] проекта с лимитерами источников.
 */
@Singleton
class PlaylistImportServiceImpl @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val spotifyProvider: SpotifyProvider,
    @SourceRateLimiter(RateLimiterKeys.DEEZER) private val deezerRateLimiter: RateLimiter,
    @SourceRateLimiter(RateLimiterKeys.APPLE) private val appleRateLimiter: RateLimiter,
) : PlaylistImportService {

    override suspend fun load(link: String): PlaylistImportResult =
        withContext(Dispatchers.IO) {
            when (val parsed = PlaylistLinkParser.parse(link)) {
                is PlaylistLinkParseResult.Failure -> PlaylistImportResult.Failure(
                    error = parsed.error,
                    message = parsed.message,
                )
                is PlaylistLinkParseResult.Success -> loadParsed(parsed)
            }
        }

    private suspend fun loadParsed(link: PlaylistLinkParseResult.Success): PlaylistImportResult =
        when (link.service) {
            ImportService.SPOTIFY -> loadSpotify(link.id)
            ImportService.DEEZER -> loadDeezer(link.id)
            ImportService.APPLE -> loadApple(link)
            ImportService.YANDEX -> failure(
                ImportError.YANDEX_UNSUPPORTED,
                "Импорт плейлистов Яндекс Музыки недоступен: нужен закрытый API",
            )
            ImportService.YOUTUBE -> loadWithNewPipe(
                service = ServiceList.YouTube,
                url = "https://www.youtube.com/playlist?list=${link.id}",
                importService = ImportService.YOUTUBE,
            )
            ImportService.SOUNDCLOUD -> loadWithNewPipe(
                service = ServiceList.SoundCloud,
                url = link.id,
                importService = ImportService.SOUNDCLOUD,
            )
        }

    /**
     * YouTube и SoundCloud через NewPipe: у обоих сервисов есть
     * `PlaylistExtractor`, поэтому логика общая — постранично набираем
     * `StreamInfoItem` и переводим их в доменные треки.
     */
    private fun loadWithNewPipe(
        service: StreamingService,
        url: String,
        importService: ImportService,
    ): PlaylistImportResult = runCatching {
        val handler = service.playlistLHFactory.fromUrl(url)
        val extractor = service.getPlaylistExtractor(handler)
        extractor.fetchPage()

        val items = mutableListOf<StreamInfoItem>()
        var page = extractor.initialPage
        items += page.items.filterIsInstance<StreamInfoItem>()
        var pages = 1
        while (pages < MAX_NEWPIPE_PAGES && page.hasNextPage()) {
            page = extractor.getPage(page.nextPage)
            items += page.items.filterIsInstance<StreamInfoItem>()
            pages++
        }

        val tracks = items.mapNotNull { it.toImportedTrack(importService) }
        successOrEmpty(extractor.name, importService, tracks)
    }.rethrowCancellation()
        .getOrElse { error -> networkFailure(error) }

    /** Строка плейлиста NewPipe → доменный трек; пустые/битые пропускаем. */
    private fun StreamInfoItem.toImportedTrack(service: ImportService): Track? {
        if (url.isBlank() || name.isBlank()) return null
        return Track(
            id = url,
            source = if (service == ImportService.YOUTUBE) Source.YOUTUBE else Source.SOUNDCLOUD,
            title = name,
            artist = uploaderName.orEmpty().ifBlank { UNKNOWN_ARTIST },
            artworkUrl = thumbnails.lastOrNull()?.url,
            streamUrl = null,
            durationMs = duration.coerceAtLeast(0L) * 1_000L,
            genre = null,
            transcodingUrl = url,
        )
    }

    /** Без ключей Spotify не ходим в сеть — сразу говорим, что делать. */
    private suspend fun loadSpotify(playlistId: String): PlaylistImportResult {
        if (!spotifyProvider.isConfigured()) {
            return failure(ImportError.SPOTIFY_NOT_CONFIGURED, SPOTIFY_NOT_CONFIGURED_MESSAGE)
        }
        return spotifyProvider.fetchPlaylist(playlistId).fold(
            onSuccess = { playlist ->
                successOrEmpty(playlist.name, ImportService.SPOTIFY, playlist.tracks)
            },
            onFailure = { error -> networkFailure(error) },
        )
    }

    private suspend fun loadDeezer(playlistId: String): PlaylistImportResult =
        runCatching {
            val body = deezerRateLimiter.acquire {
                requestBody("$DEEZER_PLAYLIST_URL$playlistId")
            }
            val playlist = PlaylistPayloadParser.deezerPlaylist(body, json)
            successOrEmpty(playlist.name, ImportService.DEEZER, playlist.tracks)
        }.rethrowCancellation()
            .getOrElse { error -> networkFailure(error) }

    /**
     * Apple Music: полноценные плейлисты недоступны без Apple Music API,
     * поэтому поддерживаем только ссылку на конкретный трек (`i=`).
     */
    private suspend fun loadApple(
        link: PlaylistLinkParseResult.Success,
    ): PlaylistImportResult {
        val trackId = link.trackId
            ?: return failure(ImportError.APPLE_PLAYLIST_UNSUPPORTED, APPLE_PLAYLIST_MESSAGE)

        return runCatching {
            val body = appleRateLimiter.acquire {
                requestBody("$ITUNES_LOOKUP_URL?id=$trackId&entity=song")
            }
            val track = PlaylistPayloadParser.appleLookupTracks(body, json).firstOrNull()
                ?: return@runCatching failure(
                    ImportError.EMPTY_PLAYLIST,
                    "Apple не вернул трек по ссылке",
                )
            PlaylistImportResult.Success(
                ImportedPlaylist(
                    name = track.title,
                    service = ImportService.APPLE,
                    tracks = listOf(track),
                ),
            )
        }.rethrowCancellation()
            .getOrElse { error -> networkFailure(error) }
    }

    private fun successOrEmpty(
        name: String,
        service: ImportService,
        tracks: List<Track>,
    ): PlaylistImportResult =
        if (tracks.isEmpty()) {
            failure(ImportError.EMPTY_PLAYLIST, "В плейлисте нет треков")
        } else {
            PlaylistImportResult.Success(ImportedPlaylist(name, service, tracks))
        }

    private fun networkFailure(error: Throwable): PlaylistImportResult {
        Log.w(TAG, "Импорт плейлиста не удался: ${error.message}", error)
        return failure(
            error = ImportError.NETWORK,
            message = error.message ?: "Не удалось загрузить плейлист",
        )
    }

    private fun failure(error: ImportError, message: String): PlaylistImportResult.Failure =
        PlaylistImportResult.Failure(error = error, message = message)

    private fun requestBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} для $url")
            return response.body?.string().orEmpty()
        }
    }

    override suspend fun loadProfile(link: String): ProfileImportResult =
        withContext(Dispatchers.IO) {
            val parsed = ProfileLinkParser.parse(link)
                ?: return@withContext ProfileImportResult.Failure(
                    ImportError.UNSUPPORTED_LINK,
                    "Ссылка должна вести на профиль Spotify или SoundCloud",
                )
            when (parsed.service) {
                ImportService.SPOTIFY -> loadSpotifyProfile(parsed.id)
                ImportService.SOUNDCLOUD -> loadSoundCloudProfile(parsed.id)
                else -> ProfileImportResult.Failure(
                    ImportError.UNSUPPORTED_LINK,
                    "Импорт профилей этого сервиса не поддерживается",
                )
            }
        }

    /** Публичные плейлисты Spotify: нужны ключи, вход не обязателен. */
    private suspend fun loadSpotifyProfile(userId: String): ProfileImportResult {
        if (!spotifyProvider.isConfigured()) {
            return ProfileImportResult.Failure(
                ImportError.SPOTIFY_NOT_CONFIGURED,
                SPOTIFY_NOT_CONFIGURED_MESSAGE,
            )
        }
        return spotifyProvider.fetchUserPlaylists(userId).fold(
            onSuccess = { playlists ->
                if (playlists.isEmpty()) {
                    ProfileImportResult.Failure(ImportError.EMPTY_PLAYLIST, NO_PUBLIC_PLAYLISTS)
                } else {
                    ProfileImportResult.Success(
                        playlists.map { playlist ->
                            ProfilePlaylist(
                                service = ImportService.SPOTIFY,
                                id = playlist.id,
                                url = "https://open.spotify.com/playlist/${playlist.id}",
                                name = playlist.name,
                                coverUrl = playlist.coverUrl,
                                trackCount = playlist.trackCount,
                            )
                        },
                    )
                }
            },
            onFailure = { error ->
                ProfileImportResult.Failure(ImportError.NETWORK, error.message.orEmpty())
            },
        )
    }

    /**
     * Публичные плейлисты SoundCloud: профиль открываем через NewPipe,
     * обходим вкладки канала и собираем `PlaylistInfoItem`.
     */
    private suspend fun loadSoundCloudProfile(profileUrl: String): ProfileImportResult =
        runCatching {
            val service = ServiceList.SoundCloud
            val handler = service.channelLHFactory.fromUrl(profileUrl)
            val extractor = service.getChannelExtractor(handler)
            extractor.fetchPage()

            val found = LinkedHashMap<String, ProfilePlaylist>()
            for (tab in extractor.tabs) {
                val tabExtractor = runCatching {
                    service.getChannelTabExtractor(tab)
                }.getOrNull() ?: continue
                tabExtractor.fetchPage()
                var page = tabExtractor.initialPage
                var pages = 0
                while (true) {
                    page.items.filterIsInstance<PlaylistInfoItem>().forEach { item ->
                        if (item.url.isNotBlank() && item.name.isNotBlank()) {
                            found.putIfAbsent(
                                item.url,
                                ProfilePlaylist(
                                    service = ImportService.SOUNDCLOUD,
                                    id = item.url,
                                    url = item.url,
                                    name = item.name,
                                    coverUrl = item.thumbnails.lastOrNull()?.url,
                                    trackCount = item.streamCount.coerceAtLeast(0L).toInt(),
                                ),
                            )
                        }
                    }
                    if (!page.hasNextPage() || pages++ >= MAX_NEWPIPE_PAGES) break
                    page = tabExtractor.getPage(page.nextPage)
                }
            }
            if (found.isEmpty()) {
                ProfileImportResult.Failure(ImportError.EMPTY_PLAYLIST, NO_PUBLIC_PLAYLISTS)
            } else {
                ProfileImportResult.Success(found.values.toList())
            }
        }.rethrowCancellation()
            .getOrElse { error ->
                ProfileImportResult.Failure(ImportError.NETWORK, error.message.orEmpty())
            }

    private companion object {
        const val TAG = "PlaylistImportService"

        /** Сколько страниц NewPipe максимум листаем: 5 × ~100 треков. */
        const val MAX_NEWPIPE_PAGES = 5
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val DEEZER_PLAYLIST_URL = "https://api.deezer.com/playlist/"
        const val ITUNES_LOOKUP_URL = "https://itunes.apple.com/lookup"
        const val SPOTIFY_NOT_CONFIGURED_MESSAGE = "Добавьте ключи Spotify в настройках"
        const val NO_PUBLIC_PLAYLISTS = "В профиле нет публичных плейлистов"
        const val APPLE_PLAYLIST_MESSAGE =
            "Импорт плейлистов Apple Music недоступен без Apple Music API"
    }
}
