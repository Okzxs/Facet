package com.facet.music.data.source.lyrics

import com.facet.music.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslationEngine @Inject constructor(
    @ApiClient private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun translate(
        trackKey: String,
        lines: List<String>,
        targetLanguage: String,
    ): List<String>? = withContext(Dispatchers.IO) {
        val target = targetLanguage.trim().ifBlank { "en" }
        val source = lines.map { it.trim() }
        val nonBlank = source.withIndex().filter { it.value.isNotEmpty() }
        if (nonBlank.isEmpty()) return@withContext source
        val key = "$trackKey|$target|${source.joinToString("\u0001").hashCode()}"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val translated = ArrayList<String>(nonBlank.size)
        for (batch in batches(nonBlank.map { it.value })) {
            val answer = request(batch, target) ?: return@withContext null
            val parts = answer.split('\n').map { it.trim() }
            if (parts.size != batch.size) return@withContext null
            translated += parts
        }
        var cursor = 0
        val result = source.map { line ->
            if (line.isEmpty()) "" else translated.getOrNull(cursor++).orEmpty()
        }
        synchronized(cache) {
            cache[key] = result
            while (cache.size > 12) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        result
    }

    private fun batches(lines: List<String>): List<List<String>> {
        val result = ArrayList<List<String>>()
        var current = ArrayList<String>()
        var chars = 0
        lines.forEach { line ->
            if (current.isNotEmpty() && chars + line.length + 1 > MAX_BATCH_CHARS) {
                result += current
                current = ArrayList()
                chars = 0
            }
            current += line
            chars += line.length + 1
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun request(lines: List<String>, target: String): String? {
        return runCatching {
            val form = FormBody.Builder()
                .add("client", "gtx")
                .add("sl", "auto")
                .add("tl", target)
                .add("dt", "t")
                .add("q", lines.joinToString("\n"))
                .build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .post(form)
                .header("User-Agent", "Facet/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val root = json.parseToJsonElement(body).jsonArray
                val segments = root.getOrNull(0)?.jsonArray ?: return@use null
                segments.mapNotNull { segment ->
                    segment.jsonArray.getOrNull(0)?.jsonPrimitive?.content
                }.joinToString("")
            }
        }.getOrNull()?.replace("\r\n", "\n")?.replace('\r', '\n')
    }

    private val cache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) = size > 12
    }

    private companion object {
        const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        const val MAX_BATCH_CHARS = 3_500
    }
}
