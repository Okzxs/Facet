package com.facet.music.data.source.external

import com.facet.music.core.import.SoundCloudLikesParser
import com.facet.music.core.network.ApiClient
import com.facet.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class SoundCloudLikesResult(
    val tracks: List<Track>,
    val likesCount: Int,
    val likeTimes: Map<String, Long> = emptyMap(),
)

class SoundCloudApiException(
    val code: Int,
    body: String,
) : IOException("SoundCloud HTTP $code: ${body.take(160)}")

class SoundCloudLikesPrivateException :
    IOException("SoundCloud likes are hidden for this profile")

@Singleton
class SoundCloudLikesProvider @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun fetchLikes(
        profileUrl: String,
        limit: Int = MAX_LIKES,
    ): Result<SoundCloudLikesResult> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = SoundcloudParsingHelper.clientId()
                ?: error("SoundCloud не выдал client_id")
            val profile = resolveProfile(profileUrl, clientId)
            val result = LinkedHashMap<String, SoundCloudLikesParser.LikeItem>()
            var next: String? = "$API/users/${profile.userId}/track_likes" +
                "?limit=$PAGE_SIZE&client_id=$clientId"

            while (next != null && result.size < limit) {
                val body = try {
                    get(next, clientId)
                } catch (e: SoundCloudApiException) {
                    if (e.code == 401 || e.code == 403) throw SoundCloudLikesPrivateException()
                    throw e
                }
                val page = SoundCloudLikesParser.parse(body, json)
                page.likes.forEach { like -> result.putIfAbsent(like.track.id, like) }
                if (page.likes.isEmpty()) break
                next = page.nextHref
            }
            val likes = result.values.take(limit).toList()
            SoundCloudLikesResult(
                tracks = likes.map { it.track },
                likesCount = profile.likesCount,
                likeTimes = likes.associate { it.track.id to it.likedAtMillis },
            )
        }
    }

    private data class Profile(val userId: Long, val likesCount: Int)

    private fun resolveProfile(profileUrl: String, clientId: String): Profile {
        val normalized = normalize(profileUrl)
        val url = "$API/resolve?url=${encode(normalized)}&client_id=$clientId"
        val body = get(url, clientId)
        val root = json.parseToJsonElement(body).jsonObject
        val id = root["id"]?.jsonPrimitive?.longOrNull
            ?: error("Профиль SoundCloud не найден")
        val likes = root["likes_count"]?.jsonPrimitive?.intOrNull ?: -1
        return Profile(userId = id, likesCount = likes)
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("soundcloud.com") || trimmed.startsWith("www.soundcloud.com") ->
                "https://$trimmed"
            else -> "https://soundcloud.com/${trimmed.trimStart('@')}"
        }
    }

    private fun get(url: String, clientId: String): String {
        val withClientId = if (url.contains("client_id=")) url else {
            val separator = if (url.contains('?')) '&' else '?'
            "$url${separator}client_id=$clientId"
        }
        val request = Request.Builder()
            .url(withClientId)
            .header("User-Agent", "Facet/1.0")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SoundCloudApiException(response.code, body)
            }
            return body
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API = "https://api-v2.soundcloud.com"
        const val PAGE_SIZE = 50
        const val MAX_LIKES = 5000
    }
}
