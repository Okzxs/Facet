package com.my.music.data.source.youtube

import android.util.Log
import androidx.media3.common.MimeTypes
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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
class YouTubeRepositoryImpl @Inject constructor() : YouTubeRepository {

    private val tag = "YouTubeRepo"

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val queryHandler = service.searchQHFactory
                    .fromQuery(query, listOf("videos"), "")

                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                Log.d(tag, "Поиск '$query': ${searchInfo.relatedItems.size} результатов")

                searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
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
                val streamInfo = StreamInfo.getInfo(service, videoUrl)

                Log.d(tag, "=== Аудиопотоки для $videoUrl ===")
                streamInfo.audioStreams.forEachIndexed { index, stream ->
                    Log.d(
                        tag,
                        "[$index] deliveryMethod=${stream.deliveryMethod}" +
                            " format=${stream.format}" +
                            " bitrate=${stream.averageBitrate}" +
                            " url=${stream.content.take(80)}",
                    )
                }
                streamInfo.errors.forEach { error ->
                    Log.e(
                        tag,
                        "StreamInfo error: ${error.javaClass.simpleName}: ${error.message}",
                        error,
                    )
                }

                val progressive = streamInfo.audioStreams.filter { stream ->
                    stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                        stream.content.isNotBlank() &&
                        stream.format.isSupportedAudio()
                }

                // M4A (AAC) — максимально совместимый вариант для Android.
                val m4a = progressive
                    .filter { it.format == MediaFormat.M4A }
                    .maxByOrNull { it.averageBitrate }
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
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = duration * 1000L,
            genre = null,
            transcodingUrl = url,
        )
    }

    companion object {
        /** Текст ошибки, когда YouTube не отдал одиночный progressive-поток. */
        const val ERROR_PROGRESSIVE_UNAVAILABLE =
            "YouTube не отдал progressive-аудио для этого трека"
    }
}
