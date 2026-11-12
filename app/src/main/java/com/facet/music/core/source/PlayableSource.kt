package com.facet.music.core.source

import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import javax.inject.Inject
import javax.inject.Singleton

data class SourceCapabilities(
    val search: Boolean = true,
    val fullStream: Boolean = false,
    val requiresAuth: Boolean = false,
    val supportsDownload: Boolean = false,
)

data class PlayableArtist(
    val name: String,
    val artworkUrl: String?,
    val source: Source,
    val id: String? = null,
)

data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val isHls: Boolean,
    val expiresAtMs: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val source: Source? = null,
)

interface PlayableSource {

    val source: Source

    val capabilities: SourceCapabilities

    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        page: Int = 1,
        useCache: Boolean = true,
    ): Result<List<Track>>

    suspend fun searchPage(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        nextToken: String? = null,
        useCache: Boolean = true,
    ): Result<TrackPage> = search(query, limit, 1, useCache).map { TrackPage(it) }

    suspend fun searchArtists(
        query: String,
        limit: Int = ARTIST_LIMIT,
    ): Result<List<Artist>> = Result.success(emptyList())

    suspend fun isConfigured(): Boolean = true

    suspend fun resolveStream(track: Track): Result<ResolvedStream> =
        Result.failure(UnsupportedOperationException("${source.name}: поток недоступен"))

    companion object {
        const val DEFAULT_LIMIT = 20
        const val ARTIST_LIMIT = 10
    }
}

@Singleton
class PlayableSourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards PlayableSource>,
) {

    private val bySource: Map<Source, PlayableSource> = sources.associateBy { it.source }

    fun provider(source: Source): PlayableSource? = bySource[source]

    fun all(): List<PlayableSource> = bySource.values.toList()

    fun capabilities(source: Source): SourceCapabilities? = bySource[source]?.capabilities
}
