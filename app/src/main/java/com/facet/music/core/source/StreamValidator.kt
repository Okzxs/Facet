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

    private data class Cached(val playable: Boolean, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun isPlayable(url: String, isHls: Boolean): Boolean {
        if (url.isBlank()) return false
        val now = System.currentTimeMillis()
        cache[url]?.let { if (it.expiresAtMs > now) return it.playable }
        val playable = withContext(Dispatchers.IO) { probe(url, isHls) }
        cache[url] = Cached(playable, now + TTL_MS)
        if (cache.size > MAX_CACHE) cache.clear()
        return playable
    }

    private fun probe(url: String, isHls: Boolean): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-2047")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("text/html")) return false
            val body = response.peekBody(PROBE_BYTES).bytes()
            val hls = isHls || contentType.contains("mpegurl") || url.contains(".m3u8")
            if (hls) {
                body.toString(Charsets.UTF_8).trimStart().startsWith("#EXTM3U")
            } else {
                looksLikeAudio(body, contentType)
            }
        }
    }.getOrDefault(false)

    companion object {
        const val PROBE_BYTES = 2048L
        const val TTL_MS = 10 * 60 * 1000L
        const val MAX_CACHE = 200

        fun looksLikeAudio(bytes: ByteArray, contentType: String): Boolean {
            val type = contentType.lowercase()
            if (type.startsWith("audio/")) return true
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
