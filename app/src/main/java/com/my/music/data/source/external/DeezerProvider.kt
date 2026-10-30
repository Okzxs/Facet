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
import kotlinx.serialization.json.intOrNull
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
 * Deezer — бесплатный публичный поиск без авторизации.
 *
 * Отдаёт полноценные метаданные и **30-секундный** MP3-превью (`preview`).
 * Полный трек требует ARL-cookie и приватного API — это сознательно не делаем:
 * играем превью, а при его отсутствии [com.my.music.core.source.StreamResolver]
 * ищет трек на YouTube.
 *
 * Успешные ответы поиска кэшируются как сырой JSON (Room, TTL 24 ч),
 * а сеть вызывается не чаще раза в секунду ([RateLimiter]).
 */
@Singleton
class DeezerProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val searchCache: SearchCacheRepository,
    @SourceRateLimiter(RateLimiterKeys.DEEZER) private val rateLimiter: RateLimiter,
) : MusicSourceProvider {

    override val source: Source = Source.DEEZER

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
            }.onFailure { error -> Log.w(TAG, "Поиск Deezer не удался: ${error.message}") }
        }

    /**
     * Превью Deezer живёт ограниченное время. Если ссылка протухла, ищем трек
     * заново по ISRC (точнее всего) или по «артист — название».
     */
    override suspend fun resolve(track: Track): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = track.isrc?.takeIf { it.isNotBlank() }
                    ?: listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" ")
                val url = "$BASE_URL/search?q=${encode(query)}&limit=$RESOLVE_LIMIT"
                // Ссылка превью одноразовая: кэш тут только помешал бы, поэтому
                // ограничиваем лишь частоту запроса.
                val root = rateLimiter.acquire { requestJson(url) }
                val refresh = root["data"]?.jsonArray
                    ?.mapNotNull { parseTrack(it.jsonObject) }
                    ?.firstOrNull { candidate ->
                        candidate.isrc != null && candidate.isrc == track.isrc
                    }
                    ?: root["data"]?.jsonArray
                        ?.mapNotNull { parseTrack(it.jsonObject) }
                        ?.firstOrNull()
                val streamUrl = refresh?.streamUrl
                    ?: error("Deezer не отдал превью для «${track.title}»")
                AudioStreamResult(url = streamUrl, isHls = false, mimeType = MIME_MP3)
            }.onFailure { error -> Log.w(TAG, "Resolve Deezer не удался: ${error.message}") }
        }

    /** Кэш → сеть. В кэш попадает только успешно разобранный ответ. */
    private suspend fun loadTracks(query: String, limit: Int, useCache: Boolean): List<Track> {
        if (useCache) {
            searchCache.get(query, source)?.let { cached ->
                // Битый кэш не должен ломать поиск: молча уходим в сеть.
                runCatching { parseTracks(cached) }.getOrNull()?.let { return it }
            }
        }

        val url = "$BASE_URL/search?q=${encode(query)}&limit=${limit.coerceIn(1, MAX_LIMIT)}"
        val body = rateLimiter.acquire { requestBody(url) }
        // Разбор идёт до записи в кэш: ответ, который не парсится, — не успех.
        val tracks = parseTracks(body)
        if (useCache) searchCache.put(query, source, body)
        return tracks
    }

    /** Разбор тела ответа — общий для свежего и закэшированного JSON. */
    private fun parseTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.mapNotNull { element -> parseTrack(element.jsonObject) }
            .orEmpty()

    private fun parseTrack(item: JsonObject): Track? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()
        val album = item["album"]?.jsonObject
        val artwork = album?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: album?.get("cover_medium")?.jsonPrimitive?.contentOrNull
        val durationSec = item["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val preview = item["preview"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        return Track(
            id = "$ID_PREFIX$id",
            source = Source.DEEZER,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            streamUrl = preview,
            durationMs = durationSec * 1000L,
            genre = null,
            transcodingUrl = "https://www.deezer.com/track/$id",
            isrc = item["isrc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
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
            if (!response.isSuccessful) error("Deezer HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "DeezerProvider"
        const val BASE_URL = "https://api.deezer.com"
        const val ID_PREFIX = "deezer:"
        const val MAX_LIMIT = 50
        const val RESOLVE_LIMIT = 10
        const val PREVIEW_SECONDS = 30
        const val MIME_MP3 = "audio/mpeg"
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
    }
}
