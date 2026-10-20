package com.my.music.data.source.lastfm

import android.util.Log
import com.my.music.BuildConfig
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.repository.LastFmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last.fm реализация. Ключ берётся из BuildConfig.LASTFM_API_KEY
 * (gradle property или переменная окружения LASTFM_API_KEY).
 */
@Singleton
class LastFmRepositoryImpl @Inject constructor(
    private val api: LastFmApi,
) : LastFmRepository {

    private val apiKey = BuildConfig.LASTFM_API_KEY.trim()

    override suspend fun getSimilarArtists(artist: String, limit: Int): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val normalizedArtist = artist.trim()
            if (apiKey.isBlank() || normalizedArtist.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            runCatching {
                api.getSimilarArtists(
                    method = METHOD_SIMILAR,
                    artist = normalizedArtist,
                    apiKey = apiKey,
                    format = "json",
                    limit = limit.coerceIn(1, 20),
                ).similarartists
                    ?.artist
                    .orEmpty()
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() && !it.equals(normalizedArtist, ignoreCase = true) }
                    .distinct()
            }.rethrowCancellation()
                .onFailure { error ->
                    Log.w(TAG, "Last.fm artist.getSimilar недоступен", error)
                }
        }

    private companion object {
        const val TAG = "LastFmRepository"
        const val METHOD_SIMILAR = "artist.getsimilar"
    }
}
