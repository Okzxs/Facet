package com.facet.music.data.source.lyrics

import com.facet.music.core.network.ApiClient
import com.facet.music.data.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BetterLyricsProvider @Inject constructor(
    @ApiClient private val client: OkHttpClient,
) {
    suspend fun lookup(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("s", title)
                .addQueryParameter("a", artist)
                .apply {
                    val seconds = durationMs / 1000
                    if (seconds > 0) addQueryParameter("d", seconds.toString())
                    if (!album.isNullOrBlank()) addQueryParameter("al", album)
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Facet/1.0")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                ProviderLyrics.parse(body)
            }
        }.getOrNull()
    }

    private companion object {
        const val BASE = "https://lyrics-api.boidu.dev/getLyrics"
    }
}
