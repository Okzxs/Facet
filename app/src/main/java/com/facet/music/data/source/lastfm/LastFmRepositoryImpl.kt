package com.facet.music.data.source.lastfm

import android.util.Log
import com.facet.music.BuildConfig
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.repository.LastFmRepository
import com.facet.music.data.repository.LastFmTrack
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
                    format = FORMAT,
                    limit = limit.coerceIn(1, MAX_ARTISTS),
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

    override suspend fun getArtistTopTracks(
        artist: String,
        limit: Int,
        page: Int,
    ): Result<List<LastFmTrack>> = withContext(Dispatchers.IO) {
        val normalized = artist.trim()
        if (apiKey.isBlank() || normalized.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        runCatching {
            api.getArtistTopTracks(
                method = METHOD_ARTIST_TOP_TRACKS,
                artist = normalized,
                apiKey = apiKey,
                format = FORMAT,
                limit = limit.coerceIn(1, MAX_TRACKS),
                page = page.coerceIn(1, MAX_PAGE),
            ).toptracks?.track.orEmpty().mapNotNull { it.toDomain() }
        }.rethrowCancellation().onFailure { error ->
            Log.w(TAG, "Last.fm artist.getTopTracks недоступен", error)
        }
    }

    override suspend fun getTagTopTracks(
        tag: String,
        limit: Int,
        page: Int,
    ): Result<List<LastFmTrack>> = withContext(Dispatchers.IO) {
        val normalized = tag.trim()
        if (apiKey.isBlank() || normalized.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        runCatching {
            api.getTagTopTracks(
                method = METHOD_TAG_TOP_TRACKS,
                tag = normalized,
                apiKey = apiKey,
                format = FORMAT,
                limit = limit.coerceIn(1, MAX_TRACKS),
                page = page.coerceIn(1, MAX_PAGE),
            ).toptracks?.track.orEmpty().mapNotNull { it.toDomain() }
        }.rethrowCancellation().onFailure { error ->
            Log.w(TAG, "Last.fm tag.getTopTracks недоступен", error)
        }
    }

    override suspend fun getArtistTopTags(artist: String, limit: Int): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val normalized = artist.trim()
            if (apiKey.isBlank() || normalized.isBlank()) {
                return@withContext Result.success(emptyList())
            }
            runCatching {
                api.getArtistTopTags(
                    method = METHOD_ARTIST_TOP_TAGS,
                    artist = normalized,
                    apiKey = apiKey,
                    format = FORMAT,
                    limit = limit.coerceIn(1, MAX_TAGS),
                ).toptags?.tag.orEmpty()
                    .map { it.name.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }.rethrowCancellation().onFailure { error ->
                Log.w(TAG, "Last.fm artist.getTopTags недоступен", error)
            }
        }

    private fun LastFmTrackDto.toDomain(): LastFmTrack? {
        val title = name.trim()
        val artistName = artist?.name?.trim().orEmpty()
        if (title.isBlank() || artistName.isBlank()) return null
        return LastFmTrack(
            name = title,
            artist = artistName,
            durationMs = (duration.coerceAtLeast(0L) * 1000L),
            listeners = listeners.coerceAtLeast(0L),
        )
    }

    private companion object {
        const val TAG = "LastFmRepository"
        const val FORMAT = "json"
        const val METHOD_SIMILAR = "artist.getsimilar"
        const val METHOD_ARTIST_TOP_TRACKS = "artist.gettoptracks"
        const val METHOD_TAG_TOP_TRACKS = "tag.gettoptracks"
        const val METHOD_ARTIST_TOP_TAGS = "artist.gettoptags"
        const val MAX_ARTISTS = 20
        const val MAX_TRACKS = 50
        const val MAX_TAGS = 20
        const val MAX_PAGE = 5
    }
}
