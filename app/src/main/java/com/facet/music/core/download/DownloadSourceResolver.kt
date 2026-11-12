package com.facet.music.core.download

import com.facet.music.core.source.PlaybackResolver
import com.facet.music.core.source.ResolveError
import com.facet.music.core.source.ResolveException
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedDownload(
    val track: Track,
    val source: Source,
    val streamUrl: String,
    val mimeType: String?,
)

class DownloadSourceUnavailableException(
    message: String = DownloadMessages.SOURCE_NOT_FOUND,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

@Singleton
class DownloadSourceResolver @Inject constructor(
    private val playbackResolver: PlaybackResolver,
) {

    suspend fun resolve(track: Track): Result<ResolvedDownload> {
        if (track.source == Source.LOCAL) {
            return Result.failure(
                DownloadSourceUnavailableException("Локальный трек уже доступен офлайн"),
            )
        }

        return playbackResolver.resolve(track, requireProgressive = true).fold(
            onSuccess = { stream ->
                if (!stream.url.startsWith("http", ignoreCase = true)) {
                    return@fold Result.failure(
                        DownloadSourceUnavailableException("Трек уже доступен офлайн"),
                    )
                }
                Result.success(
                    ResolvedDownload(
                        track = track,
                        source = stream.source ?: track.source,
                        streamUrl = stream.url,
                        mimeType = stream.mimeType,
                    ),
                )
            },
            onFailure = { error ->
                Result.failure(
                    DownloadSourceUnavailableException(
                        message = (error as? ResolveException)?.error.downloadMessage()
                            ?: DownloadMessages.SOURCE_NOT_FOUND,
                        cause = error,
                    ),
                )
            },
        )
    }

    private fun ResolveError?.downloadMessage(): String = when (this) {
        ResolveError.GeoBlocked -> "Трек недоступен в вашем регионе"
        ResolveError.SubscriptionOnly -> "Трек доступен только по подписке"
        ResolveError.NoMatch, null -> DownloadMessages.SOURCE_NOT_FOUND
        ResolveError.NotAvailable -> "Трек недоступен в источнике"
        ResolveError.Network -> "Нет подключения к интернету"
        ResolveError.Unknown -> DownloadMessages.SOURCE_NOT_FOUND
    }
}
