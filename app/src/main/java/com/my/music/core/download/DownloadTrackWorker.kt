package com.my.music.core.download

import android.content.Context
import android.util.Log
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
 * Порядок работы:
 * 1. получает прямую ссылку через SoundCloudRepository.resolveStreamUrl;
 * 2. качает файл в filesDir/downloads/<безопасное имя>.mp3;
 * 3. сохраняет запись в Room, чтобы плеер мог играть трек офлайн.
 */
class DownloadTrackWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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

        val streamResult = resolveStream(track, entryPoint)
        if (streamResult == null) {
            Log.e(TAG, "Не удалось получить ссылку на поток: ${track.title}")
            return@withContext Result.failure()
        }
        // HLS — это плейлист из сегментов, одним mp3-файлом его не сохранить
        if (streamResult.isHls) {
            Log.w(TAG, "Трек доступен только по HLS, скачивание невозможно: ${track.title}")
            return@withContext Result.failure()
        }

        val downloadsDir = File(applicationContext.filesDir, DOWNLOADS_DIR)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            Log.e(TAG, "Не удалось создать каталог ${downloadsDir.absolutePath}")
            return@withContext Result.failure()
        }

        val targetFile = File(downloadsDir, downloadFileName(track.id))
        val tempFile = File(downloadsDir, "${targetFile.name}.part")

        try {
            downloadToFile(streamResult.url, tempFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Ошибка скачивания '${track.title}'", e)
            return@withContext Result.failure()
        }

        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            Log.e(TAG, "Получен пустой файл для '${track.title}'")
            return@withContext Result.failure()
        }

        // Атомарно подменяем готовый файл (старый мог остаться от прошлой версии)
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        entryPoint.downloadRepository().save(track, targetFile.absolutePath)
        setProgress(progressData(1f))
        Log.d(TAG, "Трек скачан: '${track.title}' → ${targetFile.absolutePath}")
        Result.success()
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

    private suspend fun resolveStream(
        track: Track,
        entryPoint: DownloadWorkerEntryPoint,
    ): AudioStreamResult? {
        val transcodingUrl = track.transcodingUrl
        if (!transcodingUrl.isNullOrBlank()) {
            return entryPoint.soundCloudRepository()
                .resolveStreamUrl(transcodingUrl)
                .getOrNull()
        }
        val directUrl = track.streamUrl
        if (!directUrl.isNullOrBlank() && !directUrl.startsWith("file:")) {
            return AudioStreamResult(url = directUrl, isHls = false)
        }
        return null
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
