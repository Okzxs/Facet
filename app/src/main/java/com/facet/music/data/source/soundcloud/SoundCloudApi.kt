package com.facet.music.data.source.soundcloud

import android.util.Log
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.executeCancellable
import com.facet.music.data.model.Artist
import com.facet.music.data.model.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal data class SoundCloudStream(
    val url: String,
    val isHls: Boolean,
    val mimeType: String,
    val externalId: String,
)

internal data class SoundCloudSearchPage(
    val tracks: List<Track>,
    val nextToken: String?,
)

@Singleton
class SoundCloudApi internal constructor(
    private val client: OkHttpClient,
    private val clientIds: SoundCloudClientIdProvider,
    private val apiBaseUrl: String,
) {

    @Inject
    constructor(
        @ApiClient client: OkHttpClient,
        clientIds: SoundCloudClientIdProvider,
    ) : this(client, clientIds, API)

    private val tag = "SoundCloudApi"

    private val idCache = ConcurrentHashMap<String, String>()

    private val resolveMutex = Mutex()

    suspend fun search(query: String, limit: Int): List<Track> =
        searchPage(query, limit, offset = 0).tracks

    internal suspend fun searchPage(
        query: String,
        limit: Int,
        offset: Int,
    ): SoundCloudSearchPage {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val url = buildString {
            append(apiBaseUrl)
            append("/search/tracks?q=")
            append(encode(query))
            append("&limit=")
            append(safeLimit)
            append("&offset=")
            append(offset.coerceAtLeast(0))
        }
        val tracks = SoundCloudJson.parseCollection(apiGet(url))
            .filterNot { it.isGeoBlocked }
            .map { dto ->
                rememberId(dto.permalinkUrl, dto.id)
                dto.toTrack()
            }
        val nextOffset = if (tracks.size >= safeLimit) offset + tracks.size else null
        return SoundCloudSearchPage(tracks, nextOffset?.toString())
    }

    suspend fun searchUsers(query: String, limit: Int): List<Artist> {
        val url = "$apiBaseUrl/search/users?q=${encode(query)}&limit=${limit.coerceIn(1, MAX_LIMIT)}"
        return SoundCloudJson.parseUsers(apiGet(url)).take(limit)
    }

    suspend fun userTracks(userUrl: String, limit: Int): List<Track> {
        val userId = resolveUserId(userUrl)
        val url = "$apiBaseUrl/users/$userId/tracks?limit=${limit.coerceIn(1, MAX_LIMIT)}"
        return SoundCloudJson.parseCollection(apiGet(url))
            .filterNot { it.isGeoBlocked }
            .sortedByDescending { it.playbackCount }
            .map { dto ->
                rememberId(dto.permalinkUrl, dto.id)
                dto.toTrack()
            }
            .take(limit)
    }

    private suspend fun resolveUserId(userUrl: String): String {
        val body = apiGet("$apiBaseUrl/resolve?url=${encode(userUrl)}")
        val root = SoundCloudJson.parseObject(body)
        return (root["id"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw SoundCloudException(
                SoundCloudFailure.PARSE,
                "SoundCloud не вернул id пользователя",
            )
    }

    suspend fun related(pageUrl: String, limit: Int): List<Track> {
        val id = knownId(pageUrl) ?: resolveTrack(pageUrl).id
        val url = "$apiBaseUrl/tracks/$id/related?limit=${limit.coerceIn(1, MAX_LIMIT)}"
        return SoundCloudJson.parseCollection(apiGet(url))
            .filterNot { it.isGeoBlocked }
            .map { dto ->
                rememberId(dto.permalinkUrl, dto.id)
                dto.toTrack()
            }
    }

    internal suspend fun resolveStream(
        pageUrl: String,
        numericId: String?,
        requireProgressive: Boolean,
    ): SoundCloudStream {
        val dto = if (!numericId.isNullOrBlank()) {
            try {
                parseTrackRequest("$apiBaseUrl/tracks/$numericId")
            } catch (e: SoundCloudException) {
                if (e.code == 404) resolveTrack(pageUrl) else throw e
            }
        } else {
            resolveTrack(pageUrl)
        }
        rememberId(pageUrl, dto.id)

        if (dto.isSubscriptionOnly) {
            throw SoundCloudException(
                SoundCloudFailure.SUBSCRIPTION_ONLY,
                "Трек доступен только по подписке SoundCloud Go",
            )
        }

        val transcodings = SoundCloudJson.selectTranscodings(dto, requireProgressive)
        val stream = withClientId { clientId ->
            var lastError: SoundCloudException? = null
            for (transcoding in transcodings) {
                val endpoint = SoundCloudJson.transcodingUrl(dto, transcoding, clientId)
                try {
                    val streamUrl = SoundCloudJson.parseStreamUrl(apiGetEndpoint(endpoint))
                    return@withClientId SoundCloudStream(
                        url = streamUrl,
                        isHls = transcoding.isHls,
                        mimeType = transcoding.mimeBase,
                        externalId = dto.id,
                    )
                } catch (e: SoundCloudException) {
                    if (e.code != 404) throw e
                    lastError = e
                }
            }
            throw lastError ?: SoundCloudException(
                SoundCloudFailure.UNAVAILABLE,
                "SoundCloud не отдал ни один транскодинг",
            )
        }

        Log.d(
            tag,
            "Поток выбран: hls=${stream.isHls}",
        )
        return stream
    }

    private fun knownId(pageUrl: String): String? = idCache[pageUrl]

    private fun rememberId(pageUrl: String, id: String) {
        if (pageUrl.isNotBlank()) idCache[pageUrl] = id
    }

    private suspend fun resolveTrack(pageUrl: String): SoundCloudTrackDto =
        resolveMutex.withLock {
            parseTrackRequest("$apiBaseUrl/resolve?url=${encode(pageUrl)}")
        }

    private suspend fun parseTrackRequest(url: String): SoundCloudTrackDto =
        SoundCloudJson.parseTrack(apiGet(url))

    private suspend fun apiGet(url: String): String = withClientId { clientId ->
        apiGetEndpoint(appendClientId(url, clientId))
    }

    private suspend fun <T> withClientId(block: suspend (String) -> T): T {
        var attempt = 0
        while (true) {
            val clientId = clientIds.get()
            try {
                return block(clientId)
            } catch (e: SoundCloudUnauthorized) {
                if (attempt >= 1) {
                    throw SoundCloudException(
                        SoundCloudFailure.HTTP,
                        "SoundCloud отклонил client_id",
                        code = e.code,
                        cause = e,
                    )
                }
                attempt++
                clientIds.invalidate()
            } catch (e: SoundCloudException) {
                if (e.code == 404 && attempt == 0) {
                    attempt++
                    clientIds.invalidate()
                    continue
                }
                throw e
            }
        }
    }

    private suspend fun apiGetEndpoint(url: String): String =
        client.executeCancellable(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build(),
        ) { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 401 || response.code == 403 -> throw SoundCloudUnauthorized(response.code)
                response.code == 404 -> throw SoundCloudException(
                    SoundCloudFailure.UNAVAILABLE,
                    "SoundCloud: трек не найден",
                    code = 404,
                )
                !response.isSuccessful -> throw SoundCloudException(
                    SoundCloudFailure.HTTP,
                    "SoundCloud HTTP ${response.code}: ${redact(body.take(160))}",
                    code = response.code,
                )
                else -> body
            }
        }

    private fun appendClientId(url: String, clientId: String): String {
        if (url.contains("client_id=")) return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}client_id=${encode(clientId)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun redact(value: String): String = URL_REGEX.replace(value, "<url>")

    private class SoundCloudUnauthorized(val code: Int) : IOException("SoundCloud HTTP $code")

    private companion object {
        const val API = "https://api-v2.soundcloud.com"
        const val MAX_LIMIT = 200
        val URL_REGEX = Regex("""https?://\S+""")
    }
}
