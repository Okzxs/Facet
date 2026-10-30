package com.facet.music.data.source.lyrics

import android.util.Log
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.LyricsResult
import com.facet.music.data.repository.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lrclibApi: LrclibApi,
) : LyricsRepository {

    private val tag = "LRCLIB"

    override suspend fun getLyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val durationSec = if (durationMs > 0) durationMs / 1000L else null
            val cleanQuery = cleanTrackMetadata(title, artist)

            Log.d(tag, "Requesting: ${cleanQuery.title} - ${cleanQuery.artist} - ${durationSec}s")

            // 1. Попытка GET с очищенными параметрами и длительностью
            var found = tryGetLyrics(cleanQuery.title, cleanQuery.artist, durationSec)

            // 2. Фолбэк на GET без длительности (на SoundCloud длительность часто отличается на секунды)
            if (found == null && durationSec != null) {
                Log.d(tag, "Фолбэк: запрос GET без duration (${cleanQuery.title} - ${cleanQuery.artist})")
                found = tryGetLyrics(cleanQuery.title, cleanQuery.artist, null)
            }

            // 3. Фолбэк на search по track_name и artist_name
            if (found == null) {
                Log.d(tag, "Фолбэк: поиск search (${cleanQuery.title} - ${cleanQuery.artist})")
                found = trySearchLyrics(trackName = cleanQuery.title, artistName = cleanQuery.artist.ifBlank { null })
            }

            // 4. Фолбэк на search с полнотекстовым запросом q
            if (found == null) {
                val fullQuery = "${cleanQuery.artist} ${cleanQuery.title}".trim()
                Log.d(tag, "Фолбэк: полнотекстовый поиск search q='$fullQuery'")
                found = trySearchLyrics(query = fullQuery)
            }

            // 5. Если метаданные очищались, но ничего не найдено — попытка с исходными строками
            if (found == null && (cleanQuery.title != title || cleanQuery.artist != artist)) {
                Log.d(tag, "Фолбэк: поиск с исходными строками ('$title' - '$artist')")
                found = trySearchLyrics(query = "$artist $title".trim())
            }

            if (found != null) {
                return@runCatching found
            }

            Log.w(tag, "No lyrics found for $title by $artist")
            error("Текст для '$title' не найден")
        }.rethrowCancellation()
    }

    private suspend fun tryGetLyrics(
        trackName: String,
        artistName: String,
        durationSec: Long?,
    ): LyricsResult? {
        val response: Response<LrclibResponse> = try {
            lrclibApi.getLyrics(trackName = trackName, artistName = artistName, durationSec = durationSec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сети при getLyrics: ${e.message}")
            return null
        }

        if (response.isSuccessful) {
            val body = response.body()
            Log.d(tag, "Response code: ${response.code()}, body: ${body?.syncedLyrics?.take(100)}")
            if (body != null) {
                if (body.instrumental) {
                    return LyricsResult(lines = emptyList(), isSynced = false, isInstrumental = true)
                }
                val parsed = LrcParser.parse(body.syncedLyrics, body.plainLyrics)
                if (parsed != null) return parsed
            }
        } else {
            val err = response.errorBody()?.string()
            if (response.code() != 404) {
                Log.e(tag, "Error: $err (code: ${response.code()})")
            } else {
                Log.d(tag, "Response code: 404, трек не найден по точному запросу")
            }
        }
        return null
    }

    private suspend fun trySearchLyrics(
        trackName: String? = null,
        artistName: String? = null,
        query: String? = null,
    ): LyricsResult? {
        val response: Response<List<LrclibResponse>> = try {
            lrclibApi.searchLyrics(trackName = trackName, artistName = artistName, query = query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сети при searchLyrics: ${e.message}")
            return null
        }

        if (response.isSuccessful) {
            val list = response.body().orEmpty()
            Log.d(tag, "Response code: ${response.code()}, найдено результатов: ${list.size}")
            val best = list.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                ?: list.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                ?: list.firstOrNull { it.instrumental }

            if (best != null) {
                Log.d(tag, "Выбран результат: ${best.trackName} - ${best.artistName}, synced=${!best.syncedLyrics.isNullOrBlank()}")
                if (best.instrumental) {
                    return LyricsResult(lines = emptyList(), isSynced = false, isInstrumental = true)
                }
                val parsed = LrcParser.parse(best.syncedLyrics, best.plainLyrics)
                if (parsed != null) return parsed
            }
        } else {
            val err = response.errorBody()?.string()
            Log.e(tag, "Error: $err (code: ${response.code()})")
        }
        return null
    }

    /**
     * Очистка метаданных трека от шума SoundCloud ("Artist - Title", "(Official Video)", суффиксы).
     */
    private fun cleanTrackMetadata(title: String, artist: String): CleanQuery {
        var cleanTitle = title
        var cleanArtist = artist

        // Если в названии трека зашит исполнитель: "Artist - Title"
        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                cleanArtist = parts[0].trim()
                cleanTitle = parts[1].trim()
            }
        }

        // Очистка от суффиксов в скобках
        val noiseRegex = Regex(
            "(?i)\\s*[\\[(](official\\s*(video|audio|music\\s*video|lyric\\s*video|lyrics)?|audio|lyrics|lyric\\s*video|remastered(\\s*\\d+)?|live|hd|hq|extended)[\\])]"
        )
        cleanTitle = cleanTitle.replace(noiseRegex, "").trim()

        if (cleanArtist.endsWith(" Official", ignoreCase = true)) {
            cleanArtist = cleanArtist.removeSuffix(" Official").trim()
        }

        return CleanQuery(artist = cleanArtist, title = cleanTitle)
    }

    private data class CleanQuery(val artist: String, val title: String)
}
