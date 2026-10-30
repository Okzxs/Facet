package com.my.music.data.source.external

import android.util.Log
import com.my.music.core.network.RateLimiter
import com.my.music.core.network.RateLimiterKeys
import com.my.music.core.network.SourceRateLimiter
import com.my.music.core.source.MusicSourceProvider
import com.my.music.core.source.SourceCapabilities
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.SearchCacheRepository
import com.my.music.data.repository.SettingsRepository
import com.my.music.data.repository.SpotifyAuthRepository
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

/**
 * Плейлист Spotify: название и треки.
 *
 * Стрима нет (DRM), поэтому `streamUrl` у треков пуст — ссылку найдёт
 * [com.my.music.core.source.StreamResolver] на YouTube.
 */
data class SpotifyPlaylist(
    val name: String,
    val tracks: List<Track>,
)

/**
 * Spotify — только метаданные и поиск, без стрима.
 *
 * Авторизация — Client Credentials Flow: приложение пользователя обменивает
 * `client_id`/`client_secret` на токен (`accounts.spotify.com/api/token`).
 * Токен кэшируется в памяти до истечения, на 401 делается один перезапрос.
 *
 * Аудио Spotify защищено DRM и сторонним клиентам недоступно, поэтому
 * `streamUrl` всегда null, а воспроизведение идёт через
 * [com.my.music.core.source.StreamResolver] → матч на YouTube. `resolve`
 * не переопределяем: базовый интерфейс честно отвечает «поток недоступен».
 *
 * Успешные ответы поиска кэшируются сырым JSON (Room, TTL 24 ч). Лимитер
 * общий для поиска и запроса токена: квота Spotify считается на все запросы.
 */
@Singleton
class SpotifyProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    private val spotifyAuth: SpotifyAuthRepository,
    private val searchCache: SearchCacheRepository,
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY) private val rateLimiter: RateLimiter,
) : MusicSourceProvider {

    override val source: Source = Source.SPOTIFY

    override val capabilities = SourceCapabilities(
        search = true,
        preview = false,
        fullStream = false,
        requiresAuth = true,
        supportsDownload = false,
    )

    // Токен общий для всех запросов: обновляем под мьютексом, читаем без него.
    // Mutex вместо synchronized — обновление теперь suspend (ждёт слот лимитера).
    private val tokenMutex = Mutex()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtMs: Long = 0L

    /**
     * Источник настроен, если пользователь вошёл через Spotify (OAuth)
     * или задал Client ID + Secret для поиска без входа.
     */
    override suspend fun isConfigured(): Boolean =
        spotifyAuth.authState.value.connected || credentials() != null

    override suspend fun search(
        query: String,
        limit: Int,
        useCache: Boolean,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                loadTracks(query = query, limit = limit, useCache = useCache)
            }.onFailure { error -> Log.w(TAG, "Поиск Spotify не удался: ${error.message}") }
        }

    /**
     * Плейлист по id: `/v1/playlists/{id}` даёт название, `/v1/playlists/{id}/tracks`
     * — треки. Оба запроса идут через общий лимитер (квота Spotify общая).
     */
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

    /**
     * Треки плейлиста без названия — удобный вход для импорта.
     * Название берётся тем же запросом внутри [fetchPlaylist].
     */
    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<Track>> =
        fetchPlaylist(playlistId).map { playlist -> playlist.tracks }

    /**
     * Разбор `/v1/playlists/{id}/tracks`: элемент `track` может быть null
     * (удалённый трек) или эпизодом подкаста — такие строки пропускаем.
     */
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

    /** Кэш → сеть. В кэш попадает только успешно разобранный ответ. */
    private suspend fun loadTracks(query: String, limit: Int, useCache: Boolean): List<Track> {
        if (useCache) {
            searchCache.get(query, source)?.let { cached ->
                // Битый кэш не должен ломать поиск: молча уходим в сеть.
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
        // Настройки приходят из SharedPreferences через репозиторий: источник
        // не читает файл сам, чтобы не дублировать логику загрузки.
        val settings = settingsRepository.settings.first()
        val id = settings.spotifyClientId.trim()
        val secret = settings.spotifyClientSecret.trim()
        return if (id.isEmpty() || secret.isEmpty()) null else Credentials(id, secret)
    }

    /**
     * GET с авторизацией. Приоритет — пользовательский токен OAuth: он даёт
     * доступ к личным плейлистам, а Client Credentials — только к публичным
     * данным. На 401 токен мог быть отозван: обновляем и повторяем один раз.
     */
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

    /** Разбор тела ответа — общий для свежего и закэшированного JSON. */
    private fun parseTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["tracks"]?.jsonObject
            ?.get("items")?.jsonArray
            ?.mapNotNull { parseTrack(it.jsonObject) }
            .orEmpty()

    /** access_token из кэша или новый запрос Client Credentials Flow. */
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
            // Запас в минуту: не отправляем запрос с токеном, живущим секунды.
            tokenExpiresAtMs = System.currentTimeMillis() +
                issued.expiresInMs - TOKEN_EXPIRY_MARGIN_MS
            return issued.token
        }
    }

    private suspend fun requestToken(credentials: Credentials): IssuedToken {
        // Запрос токена тоже упирается в лимит: Spotify считает квоту на все вызовы.
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

    /** `Basic base64(client_id:client_secret)` — требование Spotify. */
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
        // Из images берём самую крупную: Spotify отдаёт 64/300/640 px.
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
            // Стрима нет: ссылку найдёт StreamResolver на YouTube.
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
