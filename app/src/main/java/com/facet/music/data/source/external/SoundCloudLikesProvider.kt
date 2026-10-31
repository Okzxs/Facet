package com.facet.music.data.source.external

import com.facet.music.core.import.SoundCloudLikesParser
import com.facet.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лайки SoundCloud через внутренний API `api-v2.soundcloud.com`.
 *
 * Официальный API требует приложения и подписки, а api-v2 работает с публичным
 * `client_id`, который NewPipe добывает сам ([SoundcloudParsingHelper.clientId]).
 * Публичный профиль отдаёт треки через `/users/{id}/track_likes`; закрытый —
 * ошибку, и это честно показывается в UI.
 */
@Singleton
class SoundCloudLikesProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchLikes(
        profileUrl: String,
        limit: Int = MAX_LIKES,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = SoundcloudParsingHelper.clientId()
                ?: error("SoundCloud не выдал client_id")
            val userId = resolveUserId(profileUrl, clientId)
            val result = LinkedHashMap<String, Track>()
            var next: String? = "$API/users/$userId/track_likes" +
                "?limit=$PAGE_SIZE&client_id=$clientId"

            while (next != null && result.size < limit) {
                val page = SoundCloudLikesParser.parse(get(next, clientId), json)
                page.tracks.forEach { track -> result.putIfAbsent(track.id, track) }
                if (page.tracks.isEmpty()) break
                next = page.nextHref
            }
            result.values.take(limit).toList()
        }
    }

    /** `/resolve` превращает `soundcloud.com/user` в numeric id. */
    private fun resolveUserId(profileUrl: String, clientId: String): Long {
        val url = "$API/resolve?url=${encode(profileUrl)}&client_id=$clientId"
        val body = get(url, clientId)
        return json.parseToJsonElement(body).jsonObject["id"]
            ?.jsonPrimitive?.longOrNull
            ?: error("Профиль SoundCloud не найден")
    }

    private fun get(url: String, clientId: String): String {
        val withClientId = if (url.contains("client_id=")) url else {
            val separator = if (url.contains('?')) '&' else '?'
            "$url${separator}client_id=$clientId"
        }
        val request = Request.Builder()
            .url(withClientId)
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API = "https://api-v2.soundcloud.com"
        const val PAGE_SIZE = 50
        const val MAX_LIKES = 200
    }
}
