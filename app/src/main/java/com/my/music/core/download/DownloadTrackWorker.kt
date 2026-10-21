package com.my.music.core.download

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Фоновое скачивание трека через Media3 DownloadManager.
 *
 * Порядок работы:
 * 1. получает прямую ссылку через SoundCloudRepository.resolveStreamUrl;
 * 2. ставит DownloadRequest в Media3 — он сам качает MP3 или HLS-сегменты;
 * 3. дожидается завершения и сохраняет запись в Room.
 *
 * HLS-поток не превращается в один mp3-файл: сегменты лежат в общем
 * SimpleCache, а плеер играет их через CacheDataSource.
 */
@UnstableApi
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

            val streamResult = resolveStream(track, entryPoint)
            if (streamResult == null) {
                Log.e(TAG, "Не удалось получить ссылку на поток: ${track.title}")
                return@withContext Result.failure()
            }
            Log.d(
                TAG,
                "doWork(): поток получен, isHls=${streamResult.isHls}, " +
                    "forceRestart=${inputData.getBoolean(KEY_FORCE_RESTART, false)}",
            )

            val offlineDownloadManager = entryPoint.offlineDownloadManager()
            try {
                val requestedAt = offlineDownloadManager.enqueue(
                    track = track,
                    stream = streamResult,
                    forceRestart = inputData.getBoolean(KEY_FORCE_RESTART, false),
                )
                Log.d(TAG, "doWork(): Media3 enqueue, requestedAt=$requestedAt")

                val outcome = offlineDownloadManager.awaitCompletion(
                    trackId = track.id,
                    requestedAtMs = requestedAt,
                ) { progress ->
                    setProgress(progressData(progress))
                }
                Log.d(TAG, "doWork(): результат Media3 = $outcome")

                when (outcome) {
                    is OfflineDownloadManager.DownloadOutcome.Completed -> Unit
                    is OfflineDownloadManager.DownloadOutcome.Failed -> {
                        Log.e(
                            TAG,
                            "Media3 не смог скачать '${track.title}': ${outcome.reason}",
                        )
                        return@withContext Result.failure(
                            workDataOf(KEY_ERROR to (outcome.reason ?: "unknown")),
                        )
                    }
                }

                entryPoint.downloadRepository().save(
                    track = track,
                    mediaUrl = streamResult.url,
                    mimeType = OfflineDownloadManager.mimeTypeOf(streamResult),
                )
                setProgress(progressData(1f))
                Log.d(
                    TAG,
                    "Трек скачан: '${track.title}' (isHls=${streamResult.isHls})",
                )
                Result.success()
            } catch (e: CancellationException) {
                // Не удаляем запись Media3: при REPLACE отменяется старая задача,
                // а новая попытка сама перезапустит загрузку. Очистку при
                // пользовательской отмене делает DownloadManager.cancel().
                Log.d(TAG, "doWork(): отменён, trackId=${track.id}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка скачивания '${track.title}'", e)
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "unknown")))
            }
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

    companion object {
        private const val TAG = "DownloadWorker"

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

        /** Новая попытка пользователя: Media3-запись пересоздаётся с нуля. */
        const val KEY_FORCE_RESTART = "force_restart"

        fun progressData(progress: Float) = workDataOf(KEY_PROGRESS to progress.coerceIn(0f, 1f))
    }
}
