package com.facet.music.data.source.external

import android.util.Log
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
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
import javax.inject.Inject
import javax.inject.Singleton

data class DeezerRelease(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
)

@Singleton
class DeezerMetadataClient @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val json: Json,
    @SourceRateLimiter(RateLimiterKeys.DEEZER) private val rateLimiter: RateLimiter,
) {

    suspend fun fetchNewReleases(limit: Int = 10): Result<List<DeezerRelease>> = runCatching {
        val body = rateLimiter.acquire {
            requestBody("$BASE_URL/editorial/0/releases?limit=${limit.coerceIn(1, MAX_LIMIT)}")
        }
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val artist = item["artist"]?.jsonObject?.get("name")
                    ?.jsonPrimitive?.contentOrNull.orEmpty()
                val cover = item["cover_medium"]?.jsonPrimitive?.contentOrNull
                    ?: item["cover_big"]?.jsonPrimitive?.contentOrNull
                DeezerRelease(id = id, title = title, artist = artist, coverUrl = cover)
            }
            .orEmpty()
    }

    suspend fun fetchAlbumTracks(albumId: Long): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = rateLimiter.acquire { requestJson("$BASE_URL/album/$albumId") }
                root["tracks"]?.jsonObject?.get("data")?.jsonArray
                    ?.mapNotNull { element -> parseTrack(element.jsonObject) }
                    .orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "Альбом Deezer не загрузился: ${error.message}")
            }
        }

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

        return Track(
            id = "$ID_PREFIX$id",
            source = Source.DEEZER,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            streamUrl = null,
            durationMs = durationSec * 1000L,
            genre = null,
            transcodingUrl = "https://www.deezer.com/track/$id",
            isrc = item["isrc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            previewSeconds = null,
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

    private companion object {
        const val TAG = "DeezerMetadata"
        const val BASE_URL = "https://api.deezer.com"
        const val ID_PREFIX = "deezer:"
        const val MAX_LIMIT = 50
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
    }
}
