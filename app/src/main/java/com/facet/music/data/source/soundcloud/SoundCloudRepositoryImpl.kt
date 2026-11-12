package com.facet.music.data.source.soundcloud

import android.util.Log
import androidx.media3.common.MimeTypes
import com.facet.music.core.download.DownloadCapability
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.network.StreamExpiry
import com.facet.music.core.source.ResolveError
import com.facet.music.core.source.ResolveException
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Artist
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.SoundCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudRepositoryImpl @Inject constructor(
    private val newPipeDownloader: NewPipeDownloader,
    private val api: SoundCloudApi,
) : SoundCloudRepository {

    override val downloadCapability: DownloadCapability = DownloadCapability.PROGRESSIVE

    private val tag = "SoundCloudRepo"

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching { api.search(query, limit) }
                .recoverCatching { error ->
                    if (error is SoundCloudException && error.failure == SoundCloudFailure.PARSE) {
                        legacySearch(query, limit)
                    } else {
                        throw error
                    }
                }
                .rethrowCancellation()
        }

    override suspend fun searchPage(
        query: String,
        limit: Int,
        nextToken: String?,
    ): Result<TrackPage> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = nextToken?.toIntOrNull() ?: 0
            val page = api.searchPage(query, limit, offset)
            TrackPage(page.tracks, page.nextToken)
        }.recoverCatching { error ->
            if (error is SoundCloudException && error.failure == SoundCloudFailure.PARSE) {
                TrackPage(legacySearch(query, limit), null)
            } else {
                throw error
            }
        }.rethrowCancellation()
    }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            runCatching { api.searchUsers(query, limit) }
                .recoverCatching { error ->
                    if (error is SoundCloudException) {
                        legacyArtists(query, limit)
                    } else {
                        throw error
                    }
                }
                .rethrowCancellation()
        }

    private suspend fun legacyArtists(query: String, limit: Int): List<Artist> {
        val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
        val queryHandler = service.searchQHFactory
            .fromQuery(query, listOf("users"), "")
        val info = newPipeDownloader.withCancellation {
            SearchInfo.getInfo(service, queryHandler)
        }
        return info.relatedItems
            .filterIsInstance<ChannelInfoItem>()
            .take(limit)
            .map { it.toArtist() }
    }

    override suspend fun searchUserTracks(userUrl: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching { api.userTracks(userUrl, limit) }.rethrowCancellation()
        }

    @Suppress("DEPRECATION")
    override suspend fun getRelated(trackUrl: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching { api.related(trackUrl, limit) }
                .recoverCatching { error ->
                    if (error is SoundCloudException && error.failure == SoundCloudFailure.PARSE) {
                        legacyRelated(trackUrl, limit)
                    } else {
                        throw error
                    }
                }
                .rethrowCancellation()
        }

    override suspend fun resolveStreamUrl(
        transcodingUrl: String,
        requireProgressive: Boolean,
        numericId: String?,
    ): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                try {
                    api.resolveStream(transcodingUrl, numericId, requireProgressive)
                        .toAudioStream()
                } catch (e: SoundCloudException) {
                    Log.w(tag, "Резолв SoundCloud не удался: ${e.failure}")
                    return@runCatching e.toAudioStream(transcodingUrl, requireProgressive)
                }
            }.rethrowCancellation()
        }

    private suspend fun SoundCloudException.toAudioStream(
        pageUrl: String,
        requireProgressive: Boolean,
    ): AudioStreamResult {
        if (failure == SoundCloudFailure.PARSE) {
            return legacyResolve(pageUrl, requireProgressive)
        }
        when (failure) {
            SoundCloudFailure.GEO_BLOCKED -> throw ResolveException(
                ResolveError.GeoBlocked,
                "Трек недоступен в вашем регионе",
                this,
            )
            SoundCloudFailure.SUBSCRIPTION_ONLY -> throw ResolveException(
                ResolveError.SubscriptionOnly,
                "Трек доступен только по подписке SoundCloud Go",
                this,
            )
            SoundCloudFailure.PROGRESSIVE_UNAVAILABLE -> throw ResolveException(
                ResolveError.NotAvailable,
                ERROR_PROGRESSIVE_UNAVAILABLE,
                this,
            )
            SoundCloudFailure.UNAVAILABLE -> throw ResolveException(
                ResolveError.NotAvailable,
                "Трек недоступен в SoundCloud",
                this,
            )
            SoundCloudFailure.HTTP -> throw ResolveException(
                if (code == 404) ResolveError.NotAvailable else ResolveError.Network,
                "SoundCloud API недоступен",
                this,
            )
            SoundCloudFailure.PARSE -> error("Разбор JSON уже обработан выше")
        }
    }

    private fun SoundCloudStream.toAudioStream(): AudioStreamResult = AudioStreamResult(
        url = url,
        isHls = isHls,
        mimeType = if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.AUDIO_MPEG,
        expiresAtMs = StreamExpiry.fromUrl(url),
    )

    private suspend fun legacySearch(query: String, limit: Int): List<Track> {
        val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
        val queryHandler = service.searchQHFactory
            .fromQuery(query, listOf("tracks"), "")
        val searchInfo = newPipeDownloader.withCancellation {
            SearchInfo.getInfo(service, queryHandler)
        }
        Log.d(tag, "Поиск '$query' через NewPipe: ${searchInfo.relatedItems.size} результатов")
        return searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map { it.toDomain() }
    }

    @Suppress("DEPRECATION")
    private suspend fun legacyRelated(trackUrl: String, limit: Int): List<Track> {
        val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
        val streamInfo = newPipeDownloader.withCancellation {
            StreamInfo.getInfo(service, trackUrl)
        }
        val related = streamInfo.relatedItems
            .ifEmpty { streamInfo.relatedStreams }
        return related
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .take(limit)
            .map { it.toDomain() }
    }

    private suspend fun legacyResolve(pageUrl: String, requireProgressive: Boolean): AudioStreamResult {
        val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
        val streamInfo = newPipeDownloader.withCancellation {
            StreamInfo.getInfo(service, pageUrl)
        }

        streamInfo.errors.forEach { error ->
            Log.e("NP_ERROR", "StreamInfo: ${error.javaClass.simpleName}: ${redact(error)}")
        }

        val progressive = streamInfo.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }

        if (progressive != null) {
            return AudioStreamResult(
                url = progressive.content,
                isHls = false,
                mimeType = MimeTypes.AUDIO_MPEG,
                expiresAtMs = StreamExpiry.fromUrl(progressive.content),
            )
        }

        if (requireProgressive) {
            throw ResolveException(ResolveError.NotAvailable, ERROR_PROGRESSIVE_UNAVAILABLE)
        }

        val hls = streamInfo.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.HLS }
            .maxByOrNull { it.averageBitrate }
            ?: throw ResolveException(
                ResolveError.NotAvailable,
                "Не удалось получить поток: " +
                    (streamInfo.errors.firstOrNull()?.message?.let { redact(it) }
                        ?: "нет доступных аудио-потоков"),
            )

        return AudioStreamResult(
            url = hls.content,
            isHls = true,
            mimeType = MimeTypes.APPLICATION_M3U8,
            expiresAtMs = StreamExpiry.fromUrl(hls.content),
        )
    }

    private fun StreamInfoItem.toDomain(): Track {
        val artworkUrl = thumbnails.maxByOrNull { it.height }?.url
        return Track(
            id = url,
            source = Source.SOUNDCLOUD,
            title = name,
            artist = uploaderName ?: "Unknown",
            artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = duration * 1000L,
            genre = null,
            transcodingUrl = url,
            playCount = viewCount.coerceAtLeast(0L),
        )
    }

    private fun ChannelInfoItem.toArtist(): Artist {
        val avatar = thumbnails.maxByOrNull { it.height }?.url
        return Artist(
            id = url,
            source = Source.SOUNDCLOUD,
            name = name,
            avatarUrl = avatar,
            subscriberCount = subscriberCount.takeIf { it > 0L },
            isVerified = isVerified,
            url = url,
        )
    }

    companion object {
        const val ERROR_PROGRESSIVE_UNAVAILABLE =
            "Трек доступен только для онлайн-прослушивания"

        private val URL_REGEX = Regex("""https?://\S+""")

        fun redact(value: String): String = value.replace(URL_REGEX, "<url>")

        fun redact(error: Throwable): String =
            redact(error.message ?: error.javaClass.simpleName)
    }
}
