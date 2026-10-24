package com.my.music.data.source.soundcloud

import android.util.Log
import androidx.media3.common.MimeTypes
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.SoundCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudRepositoryImpl @Inject constructor() : SoundCloudRepository {

    private val tag = "SoundCloudRepo"

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)

                // fromQuery(запрос, фильтры_контента, фильтр_сортировки)
                // "tracks" — фильтруем только треки, не плейлисты/пользователей
                val queryHandler = service.searchQHFactory
                    .fromQuery(query, listOf("tracks"), "")

                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                Log.d(tag, "Поиск '$query': ${searchInfo.relatedItems.size} результатов")

                searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(limit)
                    .map { it.toDomain() }
            }.rethrowCancellation()
        }

    @Suppress("DEPRECATION")
    override suspend fun getRelated(trackUrl: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
                val streamInfo = StreamInfo.getInfo(service, trackUrl)
                val related = streamInfo.relatedItems
                    .ifEmpty { streamInfo.relatedStreams }

                Log.d(tag, "Related для $trackUrl: ${related.size} элементов")

                related
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.url }
                    .take(limit)
                    .map { it.toDomain() }
            }.rethrowCancellation()
        }

    /**
     * Получить прямой URL аудио-потока по URL трека на SoundCloud.
     *
     * Логика выбора:
     *  1. PROGRESSIVE (прямой MP3) с максимальным битрейтом — всегда приоритетно
     *  2. Если [requireProgressive] = true, HLS не подходит: из сегментов нельзя
     *     собрать один mp3-файл, поэтому возвращаем ошибку «только онлайн»
     *  3. HLS (m3u8) с максимальным битрейтом — фолбэк для онлайн-плеера
     *  4. Нет ни тех ни других — исключение с реальной причиной из NewPipe
     *
     * @param transcodingUrl — URL страницы трека на SC (например https://soundcloud.com/artist/track)
     * @param requireProgressive — true для скачивания: HLS-фолбэк запрещён
     * @return [AudioStreamResult] с URL и флагом isHls для корректного mimeType в ExoPlayer
     */
    override suspend fun resolveStreamUrl(
        transcodingUrl: String,
        requireProgressive: Boolean,
    ): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
                val streamInfo = try {
                    StreamInfo.getInfo(service, transcodingUrl)
                } catch (e: Throwable) {
                    Log.e("NP_ERROR", "Ошибка при вызове StreamInfo.getInfo(): ${e.message}", e)
                    if (e.cause != null) {
                        Log.e("NP_ERROR", "Причина (cause): ${e.cause?.message}", e.cause)
                    }
                    e.suppressed.forEach { suppressed ->
                        Log.e("NP_SUPPRESSED", suppressed.message, suppressed)
                    }
                    throw e
                }

                // ─── Отладочный дамп всех потоков ────────────────────────────
                Log.d(tag, "=== Потоки для $transcodingUrl ===")
                streamInfo.audioStreams.forEachIndexed { i, s ->
                    Log.d(
                        tag,
                        "[$i] deliveryMethod=${s.deliveryMethod}" +
                            " format=${s.format}" +
                            " bitrate=${s.averageBitrate}" +
                            " url=${s.content.take(80)}",
                    )
                }

                // Если NewPipe вернул ошибки в StreamInfo — логируем их
                streamInfo.errors.forEach { error ->
                    Log.e("NP_ERROR", "StreamInfo error: ${error.javaClass.simpleName}: ${error.message}", error)
                }

                // ─── Выбор потока ─────────────────────────────────────────────

                // 1) Progressive (прямой MP3)
                val progressive = streamInfo.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.averageBitrate }

                if (progressive != null) {
                    Log.d(tag, "Выбран PROGRESSIVE: bitrate=${progressive.averageBitrate}")
                    return@runCatching AudioStreamResult(
                        url = progressive.content,
                        isHls = false,
                        mimeType = MimeTypes.AUDIO_MPEG,
                    )
                }

                // 2) Для скачивания HLS не годится: нужен один mp3-файл
                if (requireProgressive) {
                    val newPipeError = streamInfo.errors.firstOrNull()?.message
                    if (streamInfo.audioStreams.isNotEmpty()) {
                        Log.w(
                            tag,
                            "Прогрессивный поток не найден, есть только " +
                                streamInfo.audioStreams.joinToString { it.deliveryMethod.name },
                        )
                        error(ERROR_PROGRESSIVE_UNAVAILABLE)
                    }
                    error(
                        "Не удалось получить поток для '$transcodingUrl': " +
                            (newPipeError ?: "нет доступных аудио-потоков"),
                    )
                }

                // 3) HLS (m3u8) — фолбэк только для онлайн-воспроизведения
                val hls = streamInfo.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.HLS }
                    .maxByOrNull { it.averageBitrate }

                if (hls != null) {
                    Log.d(tag, "Выбран HLS (фолбэк): bitrate=${hls.averageBitrate}")
                    return@runCatching AudioStreamResult(
                        url = hls.content,
                        isHls = true,
                        mimeType = MimeTypes.APPLICATION_M3U8,
                    )
                }

                // 4) Совсем нет потоков — реальная причина из NewPipe
                val newPipeError = streamInfo.errors.firstOrNull()
                val cause = newPipeError?.message ?: "нет доступных аудио-потоков"
                error("Не удалось получить поток для '$transcodingUrl': $cause")
            }.rethrowCancellation()
        }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private fun StreamInfoItem.toDomain(): Track {
        // thumbnails — List<Image> в NewPipe v0.24+
        // Берём обложку с наибольшим разрешением
        val artworkUrl = thumbnails.maxByOrNull { it.height }?.url

        return Track(
            id = url,                        // SC track URL — уникальный ID
            source = Source.SOUNDCLOUD,
            title = name,
            artist = uploaderName ?: "Unknown",
            artworkUrl = artworkUrl,
            streamUrl = null,                // разрешается отдельно через resolveStreamUrl()
            durationMs = duration * 1000L,   // NewPipe отдаёт секунды → переводим в мс
            genre = null,                    // недоступно в результатах поиска NewPipe
            transcodingUrl = url,            // SC track URL для последующего resolve
        )
    }

    companion object {
        /** Единый текст ошибки, когда progressive MP3 недоступен. */
        const val ERROR_PROGRESSIVE_UNAVAILABLE =
            "Трек доступен только для онлайн-прослушивания"
    }
}
