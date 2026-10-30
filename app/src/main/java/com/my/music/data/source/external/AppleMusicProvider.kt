package com.my.music.data.source.external

import android.util.Log
import com.my.music.core.network.RateLimiter
import com.my.music.core.network.RateLimiterKeys
import com.my.music.core.network.SourceRateLimiter
import com.my.music.core.source.MusicSourceProvider
import com.my.music.core.source.SourceCapabilities
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.SearchCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apple Music через публичный iTunes Search API.
 *
 * Авторизации нет, но полноценного стрима тоже: Apple отдаёт только
 * `previewUrl` (M4A, ~30 секунд). Полные треки защищены DRM и недоступны
 * сторонним клиентам, поэтому для альбомных версий используется
 * [com.my.music.core.source.StreamResolver] (поиск на YouTube).
 *
 * Успешные ответы поиска кэшируются как сырой JSON (Room, TTL 24 ч),
 * сеть вызывается не чаще раза в секунду ([RateLimiter]).
 */
@Singleton
class AppleMusicProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val searchCache: SearchCacheRepository,
    @SourceRateLimiter(RateLimiterKeys.APPLE) private val rateLimiter: RateLimiter,
) : MusicSourceProvider {

    override val source: Source = Source.APPLE

    override val capabilities = SourceCapabilities(
        search = true,
        preview = true,
        fullStream = false,
        requiresAuth = false,
        supportsDownload = false,
    )

    override suspend fun search(
        query: String,
        limit: Int,
        useCache: Boolean,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                loadTracks(query = query, limit = limit, useCache = useCache)
            }.onFailure { error -> Log.w(TAG, "Поиск iTunes не удался: ${error.message}") }
        }

    /** Обновление превью по прямому iTunes-id трека. */
    override suspend fun resolve(track: Track): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trackId = track.id.removePrefix(ID_PREFIX)
                val url = "$BASE_URL/lookup?id=$trackId&entity=song"
                // Ссылка превью одноразовая — кэш тут не нужен, только лимитер.
                val root = rateLimiter.acquire { requestJson(url) }
                val fresh = root["results"]?.jsonArray
                    ?.mapNotNull { parseTrack(it.jsonObject) }
                    ?.firstOrNull()
                val direct = fresh?.streamUrl
                    ?: error("Apple не отдал превью для «${track.title}»")
                AudioStreamResult(url = direct, isHls = false, mimeType = MIME_M4A)
            }.onFailure { error -> Log.w(TAG, "Resolve Apple не удался: ${error.message}") }
        }

    /** Кэш → сеть. Русский стор может не знать запрос, тогда пробуем без страны. */
    private suspend fun loadTracks(query: String, limit: Int, useCache: Boolean): List<Track> {
        if (useCache) {
            searchCache.get(query, source)?.let { cached ->
                runCatching { parseTracks(cached) }.getOrNull()?.let { return it }
            }
        }

        val country = countryFor(query)
        val firstBody = rateLimiter.acquire { requestBody(searchUrl(query, limit, country)) }
        val first = parseTracks(firstBody)
        if (first.isNotEmpty() || country == null) {
            if (useCache) searchCache.put(query, source, firstBody)
            return first
        }

        val fallbackBody = rateLimiter.acquire { requestBody(searchUrl(query, limit, null)) }
        val fallback = parseTracks(fallbackBody)
        if (useCache) searchCache.put(query, source, fallbackBody)
        return fallback
    }

    /** Разбор тела ответа — общий для свежего и закэшированного JSON. */
    private fun parseTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
            ?.mapNotNull { parseTrack(it.jsonObject) }
            .orEmpty()

    private fun searchUrl(query: String, limit: Int, country: String?): String {
        val countryParam = country?.let { "&country=$it" }.orEmpty()
        return "$BASE_URL/search?term=${encode(query)}&entity=song" +
            "&limit=${limit.coerceIn(1, MAX_LIMIT)}$countryParam"
    }

    private fun parseTrack(item: JsonObject): Track? {
        val trackId = item["trackId"]?.jsonPrimitive?.longOrNull ?: return null
        val title = item["trackName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item["artistName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val preview = item["previewUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val artwork = item["artworkUrl100"]?.jsonPrimitive?.contentOrNull
            ?.replace(THUMB_100, THUMB_600)

        return Track(
            id = "$ID_PREFIX$trackId",
            source = Source.APPLE,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            streamUrl = preview,
            durationMs = item["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
            genre = item["primaryGenreName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            transcodingUrl = item["trackViewUrl"]?.jsonPrimitive?.contentOrNull,
            previewSeconds = if (preview != null) PREVIEW_SECONDS else null,
        )
    }

    private fun requestJson(url: String): JsonObject =
        json.parseToJsonElement(requestBody(url)).jsonObject

    private fun requestBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("iTunes HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    /** Для кириллицы сначала ищем в российском сторе. */
    private fun countryFor(query: String): String? =
        if (query.any { it in CYRILLIC_RANGE }) "RU" else null

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "AppleMusicProvider"
        const val BASE_URL = "https://itunes.apple.com"
        const val ID_PREFIX = "apple:"
        const val MAX_LIMIT = 50
        const val PREVIEW_SECONDS = 30
        const val MIME_M4A = "audio/mp4"
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
        const val THUMB_100 = "100x100bb"
        const val THUMB_600 = "600x600bb"
        val CYRILLIC_RANGE = '\u0400'..'\u04FF'
    }
}
