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
 * Скачиваем только progressive MP3: HLS — это набор сегментов, из которого
 * нельзя собрать один файл. Если progressive-потока нет, задача завершается
 * ошибкой, и UI показывает крестик со Snackbar «доступен только онлайн».
 *
 * Порядок работы:
 * 1. получает прямой progressive-URL через SoundCloudRepository.resolveStreamUrl;
 * 2. качает файл в filesDir/downloads/<безопасное имя>.mp3 через OkHttp;
 * 3. сохраняет запись в Room, чтобы плеер мог играть трек офлайн.
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

            val streamResult = resolveStream(track, entryPoint).getOrElse { error ->
                Log.e(
                    TAG,
                    "Не удалось получить progressive-поток для '${track.title}': ${error.message}",
                    error,
                )
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to (error.message ?: "resolve_failed")),
                )
            }

            // Страховка: HLS нельзя сохранить одним mp3-файлом.
            if (streamResult.isHls) {
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

            val targetFile = File(downloadsDir, downloadFileName(track.id))
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
                    mimeType = MimeTypes.AUDIO_MPEG,
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
     * Разрешает поток только в progressive-режиме.
     * HLS-фолбэк для скачивания запрещён: вернётся ошибка «только онлайн».
     */
    private suspend fun resolveStream(
        track: Track,
        entryPoint: DownloadWorkerEntryPoint,
    ): kotlin.Result<AudioStreamResult> {
        val transcodingUrl = track.transcodingUrl
        if (!transcodingUrl.isNullOrBlank()) {
            return entryPoint.soundCloudRepository()
                .resolveStreamUrl(transcodingUrl, requireProgressive = true)
        }
        val directUrl = track.streamUrl
        if (!directUrl.isNullOrBlank() && !directUrl.startsWith("file:")) {
            return kotlin.Result.success(AudioStreamResult(url = directUrl, isHls = false))
        }
        return kotlin.Result.failure(
            IllegalStateException("Нет ссылки на поток для трека: ${track.id}"),
        )
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

        /** Единый текст ошибки, когда progressive MP3 недоступен. */
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
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        /**
         * Имя файла: track.id — это URL, поэтому оставляем только безопасные символы
         * и добавляем хэш, чтобы разные треки не перекрывали друг друга.
         */
        fun downloadFileName(trackId: String): String {
            val safe = trackId
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "track" }
            val hash = Integer.toHexString(trackId.hashCode())
            return "${safe}_$hash.mp3"
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
