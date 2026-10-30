package com.facet.music.core.download

import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SoundCloudRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SoundCloud как источник скачивания.
 *
 * Большинство треков отдаётся progressive MP3, поэтому источник помечен
 * PROGRESSIVE. Для HLS-only треков [resolve] вернёт ошибку, и резолвер
 * переключится на поиск версии на YouTube.
 */
@Singleton
class SoundCloudDownloadSource @Inject constructor(
    private val repository: SoundCloudRepository,
) : DownloadSource {

    override val source: Source = Source.SOUNDCLOUD
    override val capability: DownloadCapability = repository.downloadCapability

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        repository.search(query, limit)

    override suspend fun resolve(track: Track): Result<AudioStreamResult> {
        val pageUrl = track.transcodingUrl?.takeIf { it.isHttpUrl() }
        val directUrl = track.streamUrl?.takeIf { it.isHttpUrl() }

        return when {
            pageUrl != null -> repository.resolveStreamUrl(pageUrl, requireProgressive = true)
            directUrl != null -> Result.success(
                AudioStreamResult(
                    url = directUrl,
                    isHls = false,
                    mimeType = null,
                ),
            )
            else -> Result.failure(
                IllegalArgumentException("Нет ссылки SoundCloud для трека: ${track.id}"),
            )
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
}
