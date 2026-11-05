package com.facet.music.data.source.soundcloud

import android.util.Log
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.executeCancellable
import com.facet.music.data.model.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Результат лёгкого резолва SoundCloud. */
internal data class SoundCloudStream(
    val url: String,
    val isHls: Boolean,
    val mimeType: String,
    val externalId: String,
)

/**
 * SoundCloud api-v2 без NewPipe StreamInfo.
 *
 * Основной путь — один-два запроса: `/tracks/{id}` (или `/resolve` для
 * неизвестного id) → выбор транскодинга → подписанный URL. Поиск и related
 * тоже идут через API: так результатах сразу есть числовой id и permalink.
 * NewPipe остаётся запасным путём только при ошибке разбора JSON.
 */
@Singleton
class SoundCloudApi @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val clientIds: SoundCloudClientIdProvider,
) {

    private val tag = "SoundCloudApi"

    private val idCache = ConcurrentHashMap<String, String>()

    private val resolveMutex = Mutex()

    suspend fun search(query: String, limit: Int): List<Track> {
        val url = buildString {
            append(API)
            append("/search/tracks?q=")
            append(encode(query))
            append("&limit=")
            append(limit.coerceIn(1, MAX_LIMIT))
        }
        return SoundCloudJson.parseCollection(apiGet(url))
            .filterNot { it.isGeoBlocked }
            .map { dto ->
                rememberId(dto.permalinkUrl, dto.id)
                dto.toTrack()
            }
    }

    suspend fun related(pageUrl: String, limit: Int): List<Track> {
        val id = knownId(pageUrl) ?: resolveTrack(pageUrl).id
        val url = "$API/tracks/$id/related?limit=${limit.coerceIn(1, MAX_LIMIT)}"
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
                parseTrackRequest("$API/tracks/$numericId")
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

    /** id уже известен из поиска/волны — пропускаем /resolve. */
    private fun knownId(pageUrl: String): String? = idCache[pageUrl]

    private fun rememberId(pageUrl: String, id: String) {
        if (pageUrl.isNotBlank()) idCache[pageUrl] = id
    }

    private suspend fun resolveTrack(pageUrl: String): SoundCloudTrackDto =
        resolveMutex.withLock {
            parseTrackRequest("$API/resolve?url=${encode(pageUrl)}")
        }

    private suspend fun parseTrackRequest(url: String): SoundCloudTrackDto =
        SoundCloudJson.parseTrack(apiGet(url))

    /**
     * GET с client_id. На 401/403 один раз сбрасывает кэш client_id и
     * повторяет запрос; CancellationException не перехватывается.
     */
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
                // 404 на api-v2 часто означает протухший client_id, а не удалённый
                // трек: после сброса кэша делаем одну повторную попытку.
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
