package com.facet.music.core.source

import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.SoundCloudRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudPlayableSource @Inject constructor(
    private val repository: SoundCloudRepository,
) : PlayableSource {

    override val source: Source = Source.SOUNDCLOUD

    override val capabilities = SourceCapabilities(
        search = true,
        fullStream = true,
        supportsDownload = true,
    )

    override suspend fun search(
        query: String,
        limit: Int,
        page: Int,
        useCache: Boolean,
    ): Result<List<Track>> = pagedSearch(limit, page) { fetchLimit ->
        repository.search(query, fetchLimit)
    }

    override suspend fun searchPage(
        query: String,
        limit: Int,
        nextToken: String?,
        useCache: Boolean,
    ): Result<TrackPage> = repository.searchPage(query, limit, nextToken)

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        repository.searchArtists(query, limit)

    override suspend fun resolveStream(track: Track): Result<ResolvedStream> {
        if (track.isSubscriptionOnly) {
            return Result.failure(
                ResolveException(
                    ResolveError.SubscriptionOnly,
                    "Трек доступен только по подписке SoundCloud Go",
                ),
            )
        }

        val pageUrl = listOf(track.transcodingUrl, track.streamUrl, track.id)
            .filterNotNull()
            .firstOrNull { it.isHttpUrl() }
            ?: return Result.failure(
                ResolveException(ResolveError.NotAvailable, "Нет ссылки SoundCloud для трека"),
            )

        return repository.resolveStreamUrl(
            transcodingUrl = pageUrl,
            numericId = track.externalId,
        ).map { stream ->
            ResolvedStream(
                url = stream.url,
                mimeType = stream.mimeType,
                isHls = stream.isHls,
                expiresAtMs = stream.expiresAtMs
                    ?: System.currentTimeMillis() + SOUNDCLOUD_TTL_MS,
                source = Source.SOUNDCLOUD,
            )
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val SOUNDCLOUD_TTL_MS = 5 * 60 * 1000L
    }
}
