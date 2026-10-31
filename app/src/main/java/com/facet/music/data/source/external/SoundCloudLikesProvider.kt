package com.facet.music.data.source.external

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лайки SoundCloud через публичный API v2.
 *
 * Публично страница `/likes` не парсится: она рендерится JS. API требует
 * `client_id` приложения SoundCloud — пользователь создаёт своё приложение
 * и вставляет id в экран импорта. Такой id не секрет, но и не публикуется.
 */
@Singleton
class SoundCloudLikesProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchLikes(
        profileUrl: String,
        clientId: String,
        limit: Int = MAX_LIKES,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            require(clientId.isNotBlank()) { "Нужен client_id SoundCloud" }
            val resolved = get("$API/resolve?url=${encode(profileUrl)}&client_id=$clientId")
            val userId = json.parseToJsonElement(resolved).jsonObject["id"]
                ?.jsonPrimitive?.longOrNull
                ?: error("Профиль SoundCloud не найден")

            val result = LinkedHashMap<String, Track>()
            var offset = 0
            while (result.size < limit) {
                val body = get(
                    "$API/users/$userId/likes?limit=$PAGE_SIZE&offset=$offset&client_id=$clientId",
                )
                val page = json.parseToJsonElement(body).jsonArray
                if (page.isEmpty()) break
                page.forEach { element ->
                    toTrack(element.jsonObject)?.let { track ->
                        result.putIfAbsent(track.id, track)
                    }
                }
                offset += PAGE_SIZE
            }
            result.values.toList()
        }
    }

    private fun toTrack(item: kotlinx.serialization.json.JsonObject): Track? {
        val permalink = item["permalink_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val artist = item["user"]?.jsonObject?.get("username")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        val artwork = item["artwork_url"]?.jsonPrimitive?.contentOrNull
        val duration = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L
        return Track(
            id = permalink,
            source = Source.SOUNDCLOUD,
            title = title,
            artist = artist,
            artworkUrl = artwork,
            streamUrl = null,
            durationMs = duration,
            genre = null,
            transcodingUrl = permalink,
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Facet/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("SoundCloud HTTP ${response.code}: ${body.take(120)}")
            }
            return body
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API = "https://api.soundcloud.com"
        const val PAGE_SIZE = 50
        const val MAX_LIKES = 200
    }
}
