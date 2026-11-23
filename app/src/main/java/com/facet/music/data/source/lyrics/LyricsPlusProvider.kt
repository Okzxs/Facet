package com.facet.music.data.source.lyrics

import com.facet.music.core.network.ApiClient
import com.facet.music.data.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsPlusProvider @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val json: Json,
) {
    private val lastGood = AtomicReference<String?>(null)

    suspend fun lookup(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = coroutineScope {
        val hosts = lastGood.get()
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS
        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs, album) }
        }.toMutableList()
        try {
            while (pending.isNotEmpty()) {
                val (host, lines) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (!lines.isNullOrEmpty()) {
                    lastGood.set(host)
                    return@coroutineScope lines
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
                .addQueryParameter("artist", artist)
                .apply {
                    val seconds = durationMs / 1000
                    if (seconds > 0) addQueryParameter("duration", seconds.toString())
                    if (!album.isNullOrBlank()) addQueryParameter("album", album)
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
                val parsed = json.decodeFromString<Response>(body)
                parse(parsed).takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun parse(response: Response): List<LyricLine> {
        return response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            when {
                words.isNotEmpty() -> LyricLine(
                    timeMs = minOf(start, words.first().startMs),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                )
                !line.text.isNullOrBlank() -> LyricLine(
                    timeMs = start,
                    text = line.text.trim(),
                    sungUntilMs = line.duration?.takeIf { it > 0 }?.let { start + it },
                )
                else -> null
            }
        }.sortedBy { it.timeMs }
    }

    private fun mergeSyllables(syllables: List<Syllable>): List<com.facet.music.data.model.LyricWord> {
        val runs = syllables.mapNotNull { syllable ->
            val text = syllable.text ?: return@mapNotNull null
            val time = syllable.time ?: return@mapNotNull null
            TimedRun(time, time + (syllable.duration ?: 0L), text)
        }
        return wordsFromRuns(runs, spacingIsExplicit = true)
    }

    @Serializable
    data class Response(val lyrics: List<Line>? = null)

    @Serializable
    data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
    )

    @Serializable
    data class Syllable(
        val text: String? = null,
        val time: Long? = null,
        val duration: Long? = null,
    )

    private companion object {
        val MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyricsplus-seven.vercel.app",
            "https://lyrics-plus-backend.vercel.app",
        )
    }
}
