package com.facet.music.core.source

import com.facet.music.core.network.MediaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamValidator @Inject constructor(
    @MediaClient private val client: OkHttpClient,
) {

    enum class StreamKind { INVALID, PROGRESSIVE, HLS }

    private data class Cached(val kind: StreamKind, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun isPlayable(url: String, isHls: Boolean): Boolean =
        kind(url, isHls) != StreamKind.INVALID

    suspend fun kind(url: String, isHls: Boolean): StreamKind {
        if (url.isBlank()) return StreamKind.INVALID
        val now = System.currentTimeMillis()
        cache[url]?.let { if (it.expiresAtMs > now) return it.kind }
        val kind = withContext(Dispatchers.IO) { probe(url, isHls) }
        cache[url] = Cached(kind, now + TTL_MS)
        if (cache.size > MAX_CACHE) cache.clear()
        return kind
    }

    private fun probe(url: String, isHls: Boolean): StreamKind = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-2047")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return StreamKind.INVALID
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) return StreamKind.INVALID
            val body = response.peekBody(PROBE_BYTES).bytes()
            val text = body.take(64).toByteArray().toString(Charsets.UTF_8).trimStart()
            val playlist = isHls || contentType.contains("mpegurl") || url.contains(".m3u8") ||
                text.startsWith("#EXTM3U")
            if (playlist) {
                return if (body.toString(Charsets.UTF_8).trimStart().startsWith("#EXTM3U")) {
                    StreamKind.HLS
                } else {
                    StreamKind.INVALID
                }
            }
            if (looksLikeAudio(body, contentType)) StreamKind.PROGRESSIVE else StreamKind.INVALID
        }
    }.getOrDefault(StreamKind.INVALID)

    companion object {
        const val PROBE_BYTES = 2048L
        const val TTL_MS = 10 * 60 * 1000L
        const val MAX_CACHE = 200

        fun looksLikeAudio(bytes: ByteArray, contentType: String): Boolean {
            val type = contentType.lowercase()
            if (type.contains("mpegurl")) return true
            if (bytes.size < 4) return false
            if (bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
            ) {
                return true
            }
            if (bytes[0] == 'O'.code.toByte() &&
                bytes[1] == 'g'.code.toByte() &&
                bytes[2] == 'g'.code.toByte() &&
                bytes[3] == 'S'.code.toByte()
            ) {
                return true
            }
            if (bytes[0] == 'f'.code.toByte() &&
                bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() &&
                bytes[3] == 'C'.code.toByte()
            ) {
                return true
            }
            if (bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
            ) {
                return true
            }
            if (bytes.size >= 8 &&
                bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()
            ) {
                return true
            }
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFF && (second and 0xE0) == 0xE0) return true
            val head = bytes.take(16).toByteArray().toString(Charsets.UTF_8)
            return head.contains("#EXTM3U")
        }
    }
}
