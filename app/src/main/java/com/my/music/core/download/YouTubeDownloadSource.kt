package com.my.music.core.download

import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube как источник скачивания.
 *
 * NewPipe отдаёт цельный M4A/WEBM через PROGRESSIVE_HTTP, поэтому
 * источник всегда считается progressive.
 */
@Singleton
class YouTubeDownloadSource @Inject constructor(
    private val repository: YouTubeRepository,
) : DownloadSource {

    override val source: Source = Source.YOUTUBE
    override val capability: DownloadCapability = repository.downloadCapability

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        repository.search(query, limit)

    override suspend fun resolve(track: Track): Result<AudioStreamResult> {
        val pageUrl = track.transcodingUrl?.takeIf { it.isHttpUrl() }
        val directUrl = track.streamUrl?.takeIf { it.isHttpUrl() }

        return when {
            pageUrl != null -> repository.resolveStreamUrl(pageUrl)
            directUrl != null -> Result.success(
                AudioStreamResult(
                    url = directUrl,
                    isHls = false,
                    mimeType = null,
                ),
            )
            else -> Result.failure(
                IllegalArgumentException("Нет ссылки YouTube для трека: ${track.id}"),
            )
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}
