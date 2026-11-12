package com.facet.music.data.source.external

import android.util.Log
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import com.facet.music.core.source.PlayableSource
import com.facet.music.core.source.SourceCapabilities
import com.facet.music.core.spotify.SpotifyPlaylistSummary
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SearchCacheRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SpotifyAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyPlaylist(
    val name: String,
    val tracks: List<Track>,
)

@Singleton
class SpotifyProvider @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    private val spotifyAuth: SpotifyAuthRepository,
    private val searchCache: SearchCacheRepository,
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY) private val rateLimiter: RateLimiter,
) : PlayableSource {

    override val source: Source = Source.SPOTIFY

    override val capabilities = SourceCapabilities(
        search = true,
        fullStream = false,
        requiresAuth = true,
        supportsDownload = false,
    )

    private val tokenMutex = Mutex()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtMs: Long = 0L

    override suspend fun isConfigured(): Boolean =
        spotifyAuth.authState.value.connected || credentials() != null

    override suspend fun search(
        query: String,
        limit: Int,
        page: Int,
        useCache: Boolean,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            if (page > 1) return@withContext Result.success(emptyList())
            runCatching {
                loadTracks(query = query, limit = limit, useCache = useCache)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Поиск Spotify не удался: ${error.message}")
            }.rethrowCancellation()
        }

    suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val credentials = credentials()
                val id = playlistId.removePrefix(ID_PREFIX)
                val detailsBody = rateLimiter.acquire {
                    authorizedBody("$API_URL/playlists/$id", credentials)
                }
                val name = json.parseToJsonElement(detailsBody).jsonObject["name"]
                    ?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    .orEmpty()
                val tracksBody = rateLimiter.acquire {
                    authorizedBody(
                        "$API_URL/playlists/$id/tracks?limit=$MAX_PLAYLIST_TRACKS",
                        credentials,
                    )
                }
                SpotifyPlaylist(
                    name = name.ifBlank { DEFAULT_PLAYLIST_NAME },
                    tracks = parsePlaylistTracks(tracksBody),
                )
            }.onFailure { error ->
                Log.w(TAG, "Загрузка плейлиста Spotify не удалась: ${error.message}")
            }
        }

    suspend fun fetchUserPlaylists(userId: String): Result<List<SpotifyPlaylistSummary>> =
        runCatching {
            val body = rateLimiter.acquire {
                authorizedBody(
                    "$API_URL/users/${encode(userId)}/playlists?limit=50",
                    credentials(),
                )
            }
            parsePlaylistSummaries(body)
        }

    private fun parsePlaylistSummaries(body: String): List<SpotifyPlaylistSummary> =
        json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cover = item["images"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("url")
                    ?.jsonPrimitive?.contentOrNull
                val count = item["tracks"]?.jsonObject?.get("total")
                    ?.jsonPrimitive?.intOrNull ?: 0
                SpotifyPlaylistSummary(id = id, name = name, coverUrl = cover, trackCount = count)
            }
            .orEmpty()

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<Track>> =
        fetchPlaylist(playlistId).map { playlist -> playlist.tracks }

    suspend fun fetchLikedTracks(maxTracks: Int = MAX_LIKED_TRACKS): Result<List<Track>> =
        runCatching {
            if (spotifyAuth.accessToken() == null) error(LOGIN_REQUIRED_MESSAGE)
            val result = LinkedHashMap<String, Track>()
            var offset = 0
            while (result.size < maxTracks) {
                val body = rateLimiter.acquire {
                    authorizedBody(
                        "$API_URL/me/tracks?limit=$LIKED_PAGE_SIZE&offset=$offset",
                        credentials(),
                    )
                }
                val page = parsePlaylistTracks(body)
                page.forEach { track ->
                    if (track.id.isNotBlank()) result.putIfAbsent(track.id, track)
                }
                val total = json.parseToJsonElement(body).jsonObject["total"]
                    ?.jsonPrimitive?.intOrNull
                offset += LIKED_PAGE_SIZE
                if (page.isEmpty() || (total != null && offset >= total)) break
            }
            result.values.take(maxTracks)
        }

    private fun parsePlaylistTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val track = item["track"]?.jsonObject ?: return@mapNotNull null
                if (track["type"]?.jsonPrimitive?.contentOrNull == EPISODE_TYPE) {
                    return@mapNotNull null
                }
                parseTrack(track)
            }
            .orEmpty()

    private suspend fun loadTracks(query: String, limit: Int, useCache: Boolean): List<Track> {
        if (useCache) {
            searchCache.get(query, source)?.let { cached ->
                runCatching { parseTracks(cached) }.getOrNull()?.let { return it }
            }
        }

        val url = "$API_URL/search?q=${encode(query)}" +
            "&type=track&limit=${limit.coerceIn(1, MAX_LIMIT)}"
        val body = rateLimiter.acquire { authorizedBody(url, credentials()) }
        val tracks = parseTracks(body)
        if (useCache) searchCache.put(query, source, body)
        return tracks
    }

    private suspend fun credentials(): Credentials? {
        val settings = settingsRepository.settings.first()
        val id = settings.spotifyClientId.trim()
        val secret = settings.spotifyClientSecret.trim()
        return if (id.isEmpty() || secret.isEmpty()) null else Credentials(id, secret)
    }

    private suspend fun authorizedBody(url: String, credentials: Credentials?): String {
        val userToken = spotifyAuth.accessToken()
        if (userToken != null) {
            val first = get(url = url, token = userToken)
            if (first.code != HTTP_UNAUTHORIZED) return parseBody(first)
            Log.d(TAG, "Пользовательский токен отклонён, обновляем")
            val refreshed = spotifyAuth.accessToken(forceRefresh = true)
            if (refreshed != null) return parseBody(get(url = url, token = refreshed))
        }

        val creds = credentials ?: error(NOT_CONFIGURED_MESSAGE)
        val first = get(url = url, token = token(creds, forceRefresh = false))
        if (first.code != HTTP_UNAUTHORIZED) {
            return parseBody(first)
        }
        Log.d(TAG, "Токен Spotify отклонён (401), запрашиваем новый")
        val second = get(url = url, token = token(creds, forceRefresh = true))
        return parseBody(second)
    }

    private fun get(url: String, token: String): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return HttpResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
            )
        }
    }

    private fun parseBody(response: HttpResponse): String {
        if (response.code !in HTTP_OK_RANGE) error("Spotify HTTP ${response.code}")
        return response.body
    }

    private fun parseTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["tracks"]?.jsonObject
            ?.get("items")?.jsonArray
            ?.mapNotNull { parseTrack(it.jsonObject) }
            .orEmpty()

    private suspend fun token(credentials: Credentials, forceRefresh: Boolean): String {
        accessToken?.takeIf { !forceRefresh && System.currentTimeMillis() < tokenExpiresAtMs }
            ?.let { return it }
        tokenMutex.withLock {
            val fresh = accessToken
            if (!forceRefresh && fresh != null && System.currentTimeMillis() < tokenExpiresAtMs) {
                return fresh
            }
            val issued = requestToken(credentials)
            accessToken = issued.token
            tokenExpiresAtMs = System.currentTimeMillis() +
                issued.expiresInMs - TOKEN_EXPIRY_MARGIN_MS
            return issued.token
        }
    }

    private suspend fun requestToken(credentials: Credentials): IssuedToken {
        rateLimiter.awaitSlot()
        val body = "grant_type=$GRANT_TYPE".toRequestBody(FORM_MIME_TYPE)
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Authorization", "Basic ${basicAuth(credentials)}")
            .header("Content-Type", FORM_CONTENT_TYPE)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Spotify token HTTP ${response.code}")
            val root = json.parseToJsonElement(text).jsonObject
            val token = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: error("Spotify не вернул access_token")
            val expiresSeconds = root["expires_in"]?.jsonPrimitive?.longOrNull
                ?: DEFAULT_TOKEN_TTL_SECONDS
            return IssuedToken(token = token, expiresInMs = expiresSeconds * 1000L)
        }
    }

    private fun basicAuth(credentials: Credentials): String {
        val raw = "${credentials.clientId}:${credentials.clientSecret}"
        return Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    private fun parseTrack(item: JsonObject): Track? {
        val spotifyId = item["id"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val title = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item["artists"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("name")?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()
        val artwork = item["album"]?.jsonObject
            ?.get("images")?.jsonArray
            ?.mapNotNull { element ->
                val image = element.jsonObject
                val url = image["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                url to (image["width"]?.jsonPrimitive?.intOrNull ?: 0)
            }
            ?.maxByOrNull { it.second }
            ?.first

        return Track(
            id = "$ID_PREFIX$spotifyId",
            source = Source.SPOTIFY,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            streamUrl = null,
            durationMs = item["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
            genre = null,
            transcodingUrl = "$WEB_TRACK_URL$spotifyId",
            isrc = item["external_ids"]?.jsonObject
                ?.get("isrc")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() },
            previewSeconds = null,
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class Credentials(val clientId: String, val clientSecret: String)

    private data class IssuedToken(val token: String, val expiresInMs: Long)

    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        const val TAG = "SpotifyProvider"
        const val API_URL = "https://api.spotify.com/v1"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val WEB_TRACK_URL = "https://open.spotify.com/track/"
        const val ID_PREFIX = "spotify:"
        const val GRANT_TYPE = "client_credentials"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
        const val MAX_LIMIT = 20

        const val MAX_LIKED_TRACKS = 1000

        const val LIKED_PAGE_SIZE = 50

        const val LOGIN_REQUIRED_MESSAGE = "Требуется вход в Spotify"
        const val MAX_PLAYLIST_TRACKS = 100
        const val DEFAULT_PLAYLIST_NAME = "Плейлист Spotify"
        const val EPISODE_TYPE = "episode"
        const val HTTP_UNAUTHORIZED = 401
        val HTTP_OK_RANGE = 200..299
        const val DEFAULT_TOKEN_TTL_SECONDS = 3600L
        const val TOKEN_EXPIRY_MARGIN_MS = 60_000L
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
        const val NOT_CONFIGURED_MESSAGE =
            "Spotify не настроен: добавьте client_id и client_secret в настройках"
        val FORM_MIME_TYPE = FORM_CONTENT_TYPE.toMediaType()
    }
}
