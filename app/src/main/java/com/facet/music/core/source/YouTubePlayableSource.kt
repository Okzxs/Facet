package com.facet.music.core.source

import android.net.Uri
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton

/** YouTube как полноценный источник потока. */
@Singleton
class YouTubePlayableSource @Inject constructor(
    private val repository: YouTubeRepository,
) : PlayableSource {

    override val source: Source = Source.YOUTUBE

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
                ResolveException(ResolveError.NotAvailable, "Нет ссылки YouTube для трека"),
            )

        return repository.resolveStreamUrl(pageUrl).map { stream ->
            ResolvedStream(
                url = stream.url,
                mimeType = stream.mimeType,
                isHls = stream.isHls,
                expiresAtMs = expireAtMs(stream.url),
                source = Source.YOUTUBE,
            )
        }
    }

    private fun expireAtMs(url: String): Long? =
        runCatching {
            Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()?.times(1000L)
        }.getOrNull()

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}

/** Общая пагинация для источников без серверного `page`. */
internal suspend fun pagedSearch(
    limit: Int,
    page: Int,
    fetch: suspend (Int) -> Result<List<Track>>,
): Result<List<Track>> {
    if (page <= 1) return fetch(limit)
    val offset = (page - 1).toLong() * limit
    if (offset > MAX_PAGE_OFFSET) return Result.success(emptyList())
    return fetch((offset + limit).toInt().coerceAtMost(MAX_FETCH_LIMIT)).map { tracks ->
        tracks.drop(offset.toInt()).take(limit)
    }
}

private const val MAX_PAGE_OFFSET = 200L
private const val MAX_FETCH_LIMIT = 240
