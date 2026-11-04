package com.facet.music.core.source

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SoundCloudRepository
import javax.inject.Inject
import javax.inject.Singleton

/** SoundCloud как полноценный источник потока (progressive или HLS). */
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

    override suspend fun resolveStream(track: Track): Result<ResolvedStream> {
        val pageUrl = listOf(track.transcodingUrl, track.streamUrl, track.id)
            .filterNotNull()
            .firstOrNull { it.isHttpUrl() }
            ?: return Result.failure(
                ResolveException(ResolveError.NotAvailable, "Нет ссылки SoundCloud для трека"),
            )

        return repository.resolveStreamUrl(pageUrl).map { stream ->
            ResolvedStream(
                url = stream.url,
                mimeType = stream.mimeType,
                isHls = stream.isHls,
                expiresAtMs = System.currentTimeMillis() + SOUNDCLOUD_TTL_MS,
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
