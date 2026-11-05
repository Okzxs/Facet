package com.facet.music.data.source.youtube

import android.util.Log
import androidx.media3.common.MimeTypes
import com.facet.music.core.download.DownloadCapability
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.network.StreamExpiry
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube через NewPipe Extractor.
 *
 * NewPipe отдаёт аудио из adaptiveFormats как `PROGRESSIVE_HTTP`, если это
 * обычное видео (не live/OTF): URL ведёт на цельный M4A или WEBM_OPUS-файл,
 * поэтому его можно скачать одним запросом и играть без HLS.
 */
@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val newPipeDownloader: NewPipeDownloader,
    private val settingsRepository: SettingsRepository,
) : YouTubeRepository {

    override val downloadCapability: DownloadCapability = DownloadCapability.PROGRESSIVE

    private val tag = "YouTubeRepo"

    private val pageTokens = ConcurrentHashMap<String, Page>()
    private val pageSeq = AtomicLong()

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        searchPage(query, limit).map { it.tracks }

    override suspend fun searchPage(
        query: String,
        limit: Int,
        nextToken: String?,
    ): Result<TrackPage> = withContext(Dispatchers.IO) {
        runCatching {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            if (nextToken != null) {
                val page = pageTokens.remove(nextToken)
                    ?: error("Страница поиска YouTube устарела")
                val queryHandler = service.searchQHFactory
                    .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
                val more = newPipeDownloader.withCancellation {
                    SearchInfo.getMoreItems(service, queryHandler, page)
                }
                val tracks = more.items
                    .filterIsInstance<StreamInfoItem>()
                    .take(limit)
                    .map { it.toDomain() }
                return@runCatching TrackPage(tracks, rememberPage(more.nextPage))
            }

            val primary = searchStreams(service, query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
            val primaryTracks = primary.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map { it.toDomain() }

            val tracks = if (primaryTracks.size < VIDEO_FALLBACK_THRESHOLD) {
                val videos = searchStreams(service, query, YoutubeSearchQueryHandlerFactory.VIDEOS)
                val extra = videos.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toDomain() }
                (primaryTracks + extra).distinctBy { it.id }.take(limit)
            } else {
                primaryTracks
            }

            Log.d(tag, "Поиск '$query': ${tracks.size} треков")
            TrackPage(tracks, rememberPage(primary.nextPage))
        }.rethrowCancellation()
    }

    override suspend fun searchMusicSongs(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                searchStreams(service, query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
                    .relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(limit)
                    .map { it.toDomain() }
            }.rethrowCancellation()
        }

    override suspend fun searchMusicAlbums(
        query: String,
        limit: Int,
    ): Result<List<ArtistRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            searchCollections(
                service,
                query,
                YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS,
                limit,
            )
                .filterIsInstance<PlaylistInfoItem>()
                .map { it.toRelease() }
                .distinctBy { it.url }
        }.rethrowCancellation()
    }

    override suspend fun searchMusicPlaylists(
        query: String,
        limit: Int,
    ): Result<List<ArtistPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            searchCollections(
                service,
                query,
                YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS,
                limit,
            )
                .filterIsInstance<PlaylistInfoItem>()
                .map { it.toPlaylist() }
                .distinctBy { it.url }
        }.rethrowCancellation()
    }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val music = searchChannels(
                    service,
                    query,
                    YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS,
                    limit,
                )
                val hasExact = music.any {
                    com.facet.music.core.search.ArtistMerger.normalize(it.name) ==
                        com.facet.music.core.search.ArtistMerger.normalize(query)
                }
                val channels = if (!hasExact) {
                    searchChannels(
                        service,
                        query,
                        YoutubeSearchQueryHandlerFactory.CHANNELS,
                        limit,
                    )
                } else {
                    emptyList()
                }
                val artists = (music + channels).distinctBy {
                    com.facet.music.core.search.ArtistMerger.normalize(it.name)
                }.take(limit)
                Log.d(tag, "Артисты '$query': ${artists.size}")
                artists.map { it.toDomain() }
            }.rethrowCancellation()
        }

    private suspend fun searchStreams(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String,
        filter: String,
    ): SearchInfo {
        val queryHandler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        return newPipeDownloader.withCancellation {
            SearchInfo.getInfo(service, queryHandler)
        }
    }

    private suspend fun searchCollections(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String,
        filter: String,
        limit: Int,
    ): List<InfoItem> {
        val queryHandler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = newPipeDownloader.withCancellation {
            SearchInfo.getInfo(service, queryHandler)
        }
        return info.relatedItems.take(limit)
    }

    private fun PlaylistInfoItem.toRelease(): ArtistRelease = ArtistRelease(
        id = url,
        title = name,
        coverUrl = thumbnails.maxByOrNull { it.height }?.url,
        year = null,
        url = url,
        trackCount = streamCount.coerceAtLeast(0L).toInt(),
    )

    private fun PlaylistInfoItem.toPlaylist(): ArtistPlaylist = ArtistPlaylist(
        id = url,
        title = name,
        coverUrl = thumbnails.maxByOrNull { it.height }?.url,
        url = url,
        trackCount = streamCount.coerceAtLeast(0L).toInt(),
    )

    private suspend fun searchChannels(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String,
        filter: String,
        limit: Int,
    ): List<ChannelInfoItem> {
        val queryHandler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = newPipeDownloader.withCancellation {
            SearchInfo.getInfo(service, queryHandler)
        }
        return info.relatedItems
            .filterIsInstance<ChannelInfoItem>()
            .take(limit)
    }

    private fun rememberPage(page: Page?): String? {
        if (page == null) return null
        val token = "yt-${pageSeq.incrementAndGet()}"
        pageTokens[token] = page
        return token
    }

    @Suppress("DEPRECATION")
    override suspend fun getRelated(trackUrl: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val streamInfo = newPipeDownloader.withCancellation {
                    StreamInfo.getInfo(service, trackUrl)
                }
                val related = streamInfo.relatedItems
                    .ifEmpty { streamInfo.relatedStreams }

                Log.d(tag, "Похожие: ${related.size} элементов")

                related
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.url }
                    .take(limit)
                    .map { it.toDomain() }
            }.rethrowCancellation()
        }

    /**
     * Ищет progressive-аудио: сначала M4A (AAC), затем WEBM_OPUS.
     * HLS/DASH не используем: из сегментов нельзя собрать один аудиофайл.
     */
    override suspend fun resolveStreamUrl(videoUrl: String): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val streamInfo = newPipeDownloader.withCancellation {
                    StreamInfo.getInfo(service, videoUrl)
                }

                Log.d(tag, "Аудиопотоков от NewPipe: ${streamInfo.audioStreams.size}")
                streamInfo.errors.forEach { error ->
                    Log.e(
                        tag,
                        "StreamInfo: ${error.javaClass.simpleName}: ${redact(error)}",
                        error,
                    )
                }

                val progressive = streamInfo.audioStreams.filter { stream ->
                    stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                        stream.content.isNotBlank() &&
                        stream.format.isSupportedAudio()
                }

                // M4A (AAC) — максимально совместимый вариант для Android.
                // Качество из настроек: берём поток, ближайший к выбранному
                // битрейту (YouTube отдаёт фиксированный набор), при равенстве
                // предпочитаем более высокий.
                val preferredKbps = runCatching {
                    settingsRepository.settings.first().downloadQualityKbps
                }.getOrDefault(320)
                val m4aCandidates = progressive.filter { it.format == MediaFormat.M4A }
                val m4a = m4aCandidates
                    .minWithOrNull(
                        compareBy(
                            { kotlin.math.abs(it.averageBitrate / 1000 - preferredKbps) },
                            { -it.averageBitrate },
                        ),
                    )
                    ?: m4aCandidates.maxByOrNull { it.averageBitrate }
                // Запасной вариант — Opus в WebM.
                val opus = progressive
                    .filter { it.format == MediaFormat.WEBMA_OPUS }
                    .maxByOrNull { it.averageBitrate }

                val selected: AudioStream = m4a ?: opus
                    ?: error(ERROR_PROGRESSIVE_UNAVAILABLE)

                Log.d(
                    tag,
                    "Выбран PROGRESSIVE: format=${selected.format}" +
                        " bitrate=${selected.averageBitrate}",
                )
                AudioStreamResult(
                    url = selected.content,
                    isHls = false,
                    mimeType = when (selected.format) {
                        MediaFormat.M4A -> MimeTypes.AUDIO_MP4
                        MediaFormat.WEBMA_OPUS -> MimeTypes.AUDIO_WEBM
                        else -> MimeTypes.AUDIO_MP4
                    },
                    expiresAtMs = StreamExpiry.fromUrl(selected.content),
                )
            }.rethrowCancellation()
        }

    private fun MediaFormat?.isSupportedAudio(): Boolean =
        this == MediaFormat.M4A || this == MediaFormat.WEBMA_OPUS

    private fun StreamInfoItem.toDomain(): Track {
        val artworkUrl = thumbnails.maxByOrNull { it.height }?.url
        val artist = uploaderName
            ?.removeSuffix(" - Topic")
            ?.trim()
            .orEmpty()
            .ifBlank { "Unknown" }

        return Track(
            id = url,
            source = Source.YOUTUBE,
            title = name,
            artist = artist,
            artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = duration * 1000L,
            genre = null,
            transcodingUrl = url,
            playCount = viewCount.coerceAtLeast(0L),
        )
    }

    private fun ChannelInfoItem.toDomain(): Artist {
        val avatarUrl = thumbnails.maxByOrNull { it.height }?.url
        return Artist(
            id = url,
            source = Source.YOUTUBE,
            name = name,
            avatarUrl = avatarUrl,
            subscriberCount = subscriberCount.takeIf { it > 0L },
            isVerified = isVerified,
            url = url,
        )
    }

    companion object {
        /** Сколько музыкальных треков достаточно, чтобы не добирать videos. */
        private const val VIDEO_FALLBACK_THRESHOLD = 10

        /** Текст ошибки, когда YouTube не отдал одиночный progressive-поток. */
        const val ERROR_PROGRESSIVE_UNAVAILABLE =
            "YouTube не отдал progressive-аудио для этого трека"

        private val URL_REGEX = Regex("""https?://\S+""")

        /** Убирает ссылки (в том числе подписанные) из текста ошибки. */
        fun redact(error: Throwable): String =
            (error.message ?: error.javaClass.simpleName).replace(URL_REGEX, "<url>")
    }
}
