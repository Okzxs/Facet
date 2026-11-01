package com.facet.music.data.source.soundcloud

import android.util.Log
import androidx.media3.common.MimeTypes
import com.facet.music.core.download.DownloadCapability
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SoundCloudRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudRepositoryImpl @Inject constructor(
    private val newPipeDownloader: NewPipeDownloader,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : SoundCloudRepository {

    // Большинство треков SoundCloud отдаёт progressive MP3, поэтому пробуем
    // качать напрямую. Если у трека есть только HLS, resolveStreamUrl с
    // requireProgressive = true вернёт ошибку, и резолвер переключится на YouTube.
    override val downloadCapability: DownloadCapability = DownloadCapability.PROGRESSIVE

    private val tag = "SoundCloudRepo"

    override suspend fun search(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)

                // fromQuery(запрос, фильтры_контента, фильтр_сортировки)
                // "tracks" — фильтруем только треки, не плейлисты/пользователей
                val queryHandler = service.searchQHFactory
                    .fromQuery(query, listOf("tracks"), "")

                val searchInfo = newPipeDownloader.withCancellation {
                    SearchInfo.getInfo(service, queryHandler)
                }
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
                val streamInfo = newPipeDownloader.withCancellation {
                    StreamInfo.getInfo(service, trackUrl)
                }
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
                    newPipeDownloader.withCancellation {
                        StreamInfo.getInfo(service, transcodingUrl)
                    }
                } catch (e: Throwable) {
                    // Отмена — не сетевая ошибка: не засоряем лог и пробрасываем дальше.
                    if (e is CancellationException) throw e
                    Log.e("NP_ERROR", "Ошибка при вызове StreamInfo.getInfo(): ${e.message}", e)
                    if (e.cause != null) {
                        Log.e("NP_ERROR", "Причина (cause): ${e.cause?.message}", e.cause)
                    }
                    e.suppressed.forEach { suppressed ->
                        Log.e("NP_SUPPRESSED", suppressed.message, suppressed)
                    }
                    // NewPipe не умеет HLS-only треки SoundCloud: StreamInfo падает
                    // ещё до выдачи потоков. Для онлайн-плеера берём HLS напрямую
                    // из api-v2; для скачивания это по-прежнему непригодно.
                    if (!requireProgressive) {
                        return@runCatching resolveHlsViaApi(transcodingUrl)
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

    /**
     * HLS-фолбэк для треков, у которых нет progressive MP3.
     *
     * api-v2 отдаёт `media.transcodings`; каждый элемент — endpoint, который
     * в ответ на GET возвращает подписанный URL плейлиста. NewPipe такие треки
     * не разбирает и падает с «Could not get any stream», поэтому идём в API сами.
     */
    private fun resolveHlsViaApi(transcodingUrl: String): AudioStreamResult {
        val clientId = SoundcloudParsingHelper.clientId()
            ?: error("SoundCloud не выдал client_id")

        val resolveUrl = "$API/resolve?url=${encode(transcodingUrl)}&client_id=$clientId"
        val trackBody = apiGet(resolveUrl)
        val track = json.parseToJsonElement(trackBody).jsonObject
        val media = track["media"]?.jsonObject
        val transcodings = (media?.get("transcodings") ?: track["transcodings"])?.jsonArray
            ?: error("У трека нет транскодингов SoundCloud")

        val hls = transcodings
            .map { it.jsonObject }
            .filter { element ->
                element["format"]?.jsonObject
                    ?.get("protocol")?.jsonPrimitive?.content
                    ?.equals("HLS", ignoreCase = true) == true
            }
            .maxByOrNull { hlsScore(it["preset"]?.jsonPrimitive?.content.orEmpty()) }
            ?: error("У трека нет HLS-потока")

        val endpoint = hls["url"]?.jsonPrimitive?.content
            ?: error("HLS-транскодинг без URL")
        val streamUrl = json.parseToJsonElement(apiGet(endpoint))
            .jsonObject["url"]?.jsonPrimitive?.content
            ?: error("SoundCloud не вернул URL HLS-плейлиста")

        Log.d(
            tag,
            "HLS через api-v2: preset=${hls["preset"]}, url=${streamUrl.take(100)}",
        )
        return AudioStreamResult(
            url = streamUrl,
            isHls = true,
            mimeType = MimeTypes.APPLICATION_M3U8,
        )
    }

    private fun apiGet(url: String): String {
        val withClientId = if (url.contains("client_id=")) {
            url
        } else {
            val separator = if (url.contains('?')) '&' else '?'
            "$url${separator}client_id=${SoundcloudParsingHelper.clientId()}"
        }
        val request = Request.Builder()
            .url(withClientId)
            .header("User-Agent", "Facet/1.0")
            .header("Accept", "application/json")
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("SoundCloud API HTTP ${response.code}: ${body.take(160)}")
            }
            body
        }
    }

    /** Чем выше битрейт пресета, тем раньше он в списке выбора. */
    private fun hlsScore(preset: String): Int = when {
        preset.contains("160") -> 160
        preset.contains("96") -> 96
        preset.contains("abr") -> 80
        else -> 0
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

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
        const val API = "https://api-v2.soundcloud.com"

        /** Единый текст ошибки, когда progressive MP3 недоступен. */
        const val ERROR_PROGRESSIVE_UNAVAILABLE =
            "Трек доступен только для онлайн-прослушивания"
    }
}
