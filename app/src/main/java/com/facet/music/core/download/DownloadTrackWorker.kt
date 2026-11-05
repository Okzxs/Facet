package com.facet.music.core.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Фоновое скачивание трека в приватное хранилище приложения.
 *
 * Скачиваем только progressive-файлы: HLS — это набор сегментов, из которого
 * нельзя собрать один файл. Источник выбирается по [Track.source]:
 * SoundCloud отдаёт MP3, YouTube — M4A (реже WEBM/Opus). Если у
 * SoundCloud-трека есть только HLS, резолвер ищет версию на YouTube.
 * Когда progressive-потока нет нигде, задача завершается ошибкой, и UI
 * показывает крестик со Snackbar с понятным текстом.
 *
 * Воркер получает из WorkData только идентичность трека и сам вызывает
 * [DownloadSourceResolver] перед скачиванием. Так протухшая подписанная
 * ссылка не превращается в вечную ошибку: при 403/410 источник резолвится
 * ещё раз. Вся работа с файлами и повторами живёт в [DownloadEngine].
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
        val track = readTrack()
        if (track == null) {
            Log.e(TAG, "Нет входных данных трека")
            return Result.failure()
        }
        if (track.source == Source.LOCAL) {
            Log.w(TAG, "Локальный трек не нужно скачивать: ${track.id}")
            return Result.failure()
        }

        val entryPoint = DownloadWorkerEntryPoint.from(applicationContext)

        return try {
            withContext(Dispatchers.IO) {
                setProgress(progressData(0f))
                val engine = DownloadEngine(
                    downloadsDir = File(applicationContext.filesDir, DOWNLOADS_DIR),
                    client = entryPoint.mediaClient(),
                    resolve = entryPoint.downloadSourceResolver()::resolve,
                    save = { downloaded, mediaUrl, mimeType ->
                        entryPoint.downloadRepository().save(downloaded, mediaUrl, mimeType)
                    },
                    mediaUrl = { file -> Uri.fromFile(file).toString() },
                    workId = id.toString(),
                    onProgress = { progress -> setProgress(progressData(progress)) },
                )

                engine.download(track).fold(
                    onSuccess = {
                        Log.d(TAG, "Трек скачан: '${track.title}'")
                        Result.success()
                    },
                    onFailure = { error ->
                        Log.e(
                            TAG,
                            "Не удалось скачать '${track.title}': ${error.message}",
                            error,
                        )
                        val message = error.message ?: ERROR_UNKNOWN
                        if (isTransient(error) && runAttemptCount < MAX_ATTEMPTS) {
                            // Транзиентный сбой: WorkManager повторит задачу
                            // по экспоненциальному backoff из setBackoffCriteria.
                            Log.d(TAG, "Повтор #${runAttemptCount + 1} для '${track.title}'")
                            Result.retry()
                        } else {
                            Result.failure(workDataOf(KEY_ERROR to message))
                        }
                    },
                )
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "doWork(): отменён, trackId=${track.id}")
            throw e
        }
    }

    /**
     * Транзиентный сбой, после которого повтор имеет смысл: обрыв сети, таймаут,
     * 5xx и несовпавший размер файла. [DownloadException] — осознанный отказ
     * источника («только онлайн», нет каталога), повторять его бесполезно.
     */
    private fun isTransient(error: Throwable): Boolean = when (error) {
        is DownloadException -> false
        is HttpStatusException -> error.code in RETRYABLE_HTTP_CODES
        is IOException -> true
        else -> false
    }

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

    companion object {
        private const val TAG = "DownloadWorker"

        /** Единый текст ошибки, когда progressive-файл недоступен. */
        const val ERROR_ONLINE_ONLY = "Трек доступен только для онлайн-прослушивания"

        /** Текст в outputData, когда исключение пришло без сообщения. */
        const val ERROR_UNKNOWN = "download_failed"

        /** Попыток на одну задачу: дальше транзиентный сбой становится FAILED. */
        private const val MAX_ATTEMPTS = 3

        /** HTTP-коды, при которых повтор через backoff оправдан. */
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504, 509)

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
         * Имя файла — SHA-256 от track.id в hex. Это устраняет коллизии
         * 32-битного hashCode и не зависит от длины URL.
         */
        fun downloadFileName(trackId: String, extension: String = "mp3"): String {
            val safeExtension = extension
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                ?: "mp3"
            val digest = java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(trackId.toByteArray(Charsets.UTF_8))
            val hash = buildString(digest.size * 2) {
                digest.forEach { byte ->
                    val value = byte.toInt() and 0xFF
                    append(HEX[value ushr 4])
                    append(HEX[value and 0x0F])
                }
            }
            return "$hash.$safeExtension"
        }

        private const val HEX = "0123456789abcdef"

        fun progressData(progress: Float) = workDataOf(KEY_PROGRESS to progress.coerceIn(0f, 1f))
    }
}
