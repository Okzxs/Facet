package com.my.music.core.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Фоновое скачивание трека в приватное хранилище приложения.
 *
 * Скачиваем только progressive-файлы: HLS — это набор сегментов, из которого
 * нельзя собрать один файл. Источник выбирается по [Track.source]:
 * SoundCloud отдаёт MP3, YouTube — M4A (реже WEBM/Opus).
 * Если progressive-потока нет, задача завершается ошибкой, и UI показывает
 * крестик со Snackbar «доступен только онлайн».
 *
 * Порядок работы:
 * 1. получает прямой progressive-URL через репозиторий нужного источника;
 * 2. качает файл в filesDir/downloads/<безопасное имя>.<mp3|m4a|webm> через OkHttp;
 * 3. сохраняет запись и её MIME в Room, чтобы плеер мог играть трек офлайн.
 */
class DownloadTrackWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Самый первый лог: по нему видно, что WorkManager реально запустил воркер.
        Log.d(
            TAG,
            "doWork(): старт, workId=$id, trackId=${inputData.getString(KEY_TRACK_ID)}",
        )
        return withContext(Dispatchers.IO) {
            val track = readTrack()
            if (track == null) {
                Log.e(TAG, "Нет входных данных трека")
                return@withContext Result.failure()
            }
            if (track.source == Source.LOCAL) {
                Log.w(TAG, "Локальный трек не нужно скачивать: ${track.id}")
                return@withContext Result.failure()
            }

            val entryPoint = DownloadWorkerEntryPoint.from(applicationContext)
            setProgress(progressData(0f))

            // Резолвер уже выбрал источник и отдал прямой progressive-URL.
            // Старый путь resolveStream оставлен для задач, поставленных
            // до появления резолвера, и для повторного запуска WorkManager.
            val resolvedSource = readResolvedSource(track)
            val streamResult = directStreamResult()
                ?: resolveStream(track, entryPoint).getOrElse { error ->
                    Log.e(
                        TAG,
                        "Не удалось получить progressive-поток для '${track.title}': ${error.message}",
                        error,
                    )
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to (error.message ?: "resolve_failed")),
                    )
                }

            val mimeType = mimeTypeFor(resolvedSource, streamResult)
            // Страховка: HLS нельзя сохранить одним файлом.
            if (streamResult.isHls || mimeType == MimeTypes.APPLICATION_M3U8) {
                Log.w(TAG, "Трек доступен только по HLS, скачивание невозможно: ${track.title}")
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to ERROR_ONLINE_ONLY),
                )
            }

            val downloadsDir = File(applicationContext.filesDir, DOWNLOADS_DIR)
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                Log.e(TAG, "Не удалось создать каталог ${downloadsDir.absolutePath}")
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "downloads_dir_unavailable"),
                )
            }

            val extension = extensionFor(mimeType, track.source)
            val targetFile = File(downloadsDir, downloadFileName(track.id, extension))
            // Уникальный .part на попытку: при REPLACE старый воркер не должен
            // удалить недокачанный файл новой задачи.
            val tempFile = File(downloadsDir, "${targetFile.name}.$id.part")

            try {
                downloadToFile(streamResult.url, tempFile)
            } catch (e: CancellationException) {
                // Пользователь отменил задачу: недокачанный файл не нужен.
                tempFile.delete()
                Log.d(TAG, "doWork(): отменён, trackId=${track.id}")
                throw e
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "Ошибка скачивания '${track.title}'", e)
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to (e.message ?: "download_failed")),
                )
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                Log.e(TAG, "Получен пустой файл для '${track.title}'")
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "empty_file"),
                )
            }

            // Атомарно подменяем готовый файл (старый мог остаться от прошлой попытки).
            try {
                currentCoroutineContext().ensureActive()
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: CancellationException) {
                tempFile.delete()
                throw e
            } catch (e: Exception) {
                tempFile.delete()
                Log.e(TAG, "Не удалось сохранить файл трека '${track.title}'", e)
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to (e.message ?: "move_failed")),
                )
            }

            // Перед записью в Room ещё раз проверяем отмену: после rename
            // файл уже готов, но запись не должна появиться у отменённой задачи.
            try {
                currentCoroutineContext().ensureActive()
                entryPoint.downloadRepository().save(
                    track = track,
                    mediaUrl = Uri.fromFile(targetFile).toString(),
                    mimeType = mimeType,
                )
            } catch (e: CancellationException) {
                // Файл не удаляем: его могла уже перезаписать новая попытка.
                throw e
            } catch (e: Exception) {
                targetFile.delete()
                Log.e(TAG, "Не удалось сохранить запись о скачанном треке '${track.title}'", e)
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to (e.message ?: "save_failed")),
                )
            }

            setProgress(progressData(1f))
            Log.d(TAG, "Трек скачан: '${track.title}' → ${targetFile.absolutePath}")
            Result.success()
        }
    }

    /** Прямой URL, который резолвер положил в задачу. */
    private fun directStreamResult(): AudioStreamResult? {
        val url = inputData.getString(KEY_DIRECT_STREAM_URL) ?: return null
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return AudioStreamResult(
            url = url,
            isHls = false,
            mimeType = inputData.getString(KEY_DIRECT_MIME_TYPE),
        )
    }

    /** Источник, с которого реально качается файл (может отличаться от трека). */
    private fun readResolvedSource(fallback: Track): Source =
        inputData.getString(KEY_RESOLVED_SOURCE)
            ?.let { name -> runCatching { Source.valueOf(name) }.getOrNull() }
            ?: fallback.source

    /** Восстанавливает трек из входных данных WorkManager. */
    private fun readTrack(): Track? {
        val id = inputData.getString(KEY_TRACK_ID) ?: return null
        return Track(
            id = id,
            source = runCatching {
                Source.valueOf(inputData.getString(KEY_SOURCE) ?: Source.SOUNDCLOUD.name)
            }.getOrDefault(Source.SOUNDCLOUD),
            title = inputData.getString(KEY_TITLE) ?: "",
            artist = inputData.getString(KEY_ARTIST) ?: "",
            artworkUrl = inputData.getString(KEY_ARTWORK_URL),
            streamUrl = inputData.getString(KEY_STREAM_URL),
            durationMs = inputData.getLong(KEY_DURATION_MS, 0L),
            genre = null,
            liked = false,
            transcodingUrl = inputData.getString(KEY_TRANSCODING_URL),
        )
    }

    /**
     * Разрешает поток через репозиторий источника.
     * HLS-фолбэк для скачивания запрещён: вернётся ошибка «только онлайн».
     */
    private suspend fun resolveStream(
        track: Track,
        entryPoint: DownloadWorkerEntryPoint,
    ): kotlin.Result<AudioStreamResult> = when (track.source) {
        Source.YOUTUBE -> {
            // У YouTube в transcodingUrl лежит страница видео, а не аудиофайл
            val videoUrl = track.transcodingUrl ?: track.streamUrl
            if (videoUrl.isNullOrBlank()) {
                kotlin.Result.failure(
                    IllegalStateException("Нет YouTube-ссылки для трека: ${track.id}"),
                )
            } else {
                entryPoint.youTubeRepository().resolveStreamUrl(videoUrl)
            }
        }
        Source.SOUNDCLOUD -> {
            val transcodingUrl = track.transcodingUrl
            if (!transcodingUrl.isNullOrBlank()) {
                entryPoint.soundCloudRepository()
                    .resolveStreamUrl(transcodingUrl, requireProgressive = true)
            } else {
                directStream(track)
            }
        }
        Source.LOCAL -> kotlin.Result.failure(
            IllegalStateException("Локальный трек не нужно скачивать: ${track.id}"),
        )
        // Для будущих источников используем уже готовый прямой URL
        else -> directStream(track)
    }

    private fun directStream(track: Track): kotlin.Result<AudioStreamResult> {
        val directUrl = track.streamUrl
        return if (!directUrl.isNullOrBlank() && !directUrl.startsWith("file:")) {
            kotlin.Result.success(
                AudioStreamResult(
                    url = directUrl,
                    isHls = false,
                    mimeType = defaultMimeType(track.source),
                ),
            )
        } else {
            kotlin.Result.failure(
                IllegalStateException("Нет ссылки на поток для трека: ${track.id}"),
            )
        }
    }

    /**
     * Скачивает поток в [targetFile], регулярно публикуя прогресс.
     * Таймауты отличаются от API-клиента: файл может качаться дольше 15 секунд.
     */
    private suspend fun downloadToFile(url: String, targetFile: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        DOWNLOAD_CLIENT.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} при скачивании $url")
            }
            val body = response.body ?: throw IOException("Пустой ответ для $url")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (totalBytes > 0L) {
                            val percent = (downloaded * 100 / totalBytes).toInt()
                            if (percent >= lastPercent + 5 || percent == 100) {
                                lastPercent = percent
                                setProgress(progressData(percent / 100f))
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val USER_AGENT = "MyMusicApp/1.0"

        /** Единый текст ошибки, когда progressive-файл недоступен. */
        const val ERROR_ONLINE_ONLY = "Трек доступен только для онлайн-прослушивания"

        /** Подкаталог во внутреннем хранилище приложения. */
        const val DOWNLOADS_DIR = "downloads"

        const val KEY_TRACK_ID = "track_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ARTWORK_URL = "artwork_url"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_SOURCE = "source"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_TRANSCODING_URL = "transcoding_url"
        const val KEY_DIRECT_STREAM_URL = "direct_stream_url"
        const val KEY_DIRECT_MIME_TYPE = "direct_mime_type"
        const val KEY_RESOLVED_SOURCE = "resolved_source"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        /** MIME из результата resolve либо безопасный фолбэк по источнику. */
        internal fun mimeTypeFor(source: Source, result: AudioStreamResult): String =
            result.mimeType?.takeIf { it.isNotBlank() } ?: defaultMimeType(source)

        internal fun defaultMimeType(source: Source): String = when (source) {
            Source.YOUTUBE -> MimeTypes.AUDIO_MP4
            else -> MimeTypes.AUDIO_MPEG
        }

        /** Расширение файла по MIME: Room и ExoPlayer должны видеть один формат. */
        internal fun extensionFor(mimeType: String, source: Source): String = when (mimeType) {
            MimeTypes.AUDIO_MP4, MimeTypes.AUDIO_AAC -> "m4a"
            MimeTypes.AUDIO_WEBM, MimeTypes.AUDIO_OPUS -> "webm"
            MimeTypes.AUDIO_MPEG -> "mp3"
            else -> if (source == Source.YOUTUBE) "m4a" else "mp3"
        }

        /**
         * Имя файла: track.id — это URL, поэтому оставляем только безопасные символы
         * и добавляем хэш, чтобы разные треки не перекрывали друг друга.
         */
        fun downloadFileName(trackId: String, extension: String = "mp3"): String {
            val safe = trackId
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "track" }
            val hash = Integer.toHexString(trackId.hashCode())
            val safeExtension = extension
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                ?: "mp3"
            return "${safe}_$hash.$safeExtension"
        }

        fun progressData(progress: Float) = workDataOf(KEY_PROGRESS to progress.coerceIn(0f, 1f))

        private val DOWNLOAD_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                // Общий лимит на весь запрос не ставим: трек может качаться минутами
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
