package com.my.music.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Фоновый шаг после успешного скачивания:
 * локальный файл -> PCM 16 кГц моно -> эмбеддинг YAMNet -> Room.
 *
 * Воркер запускается только как продолжение [com.my.music.core.download.DownloadTrackWorker]
 * в unique work-цепочке. Ошибка анализа не должна отменять само скачивание:
 * файл уже доступен офлайн, а эмбеддинг можно пересчитать позже.
 */
class AudioFeatureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID)
        if (trackId.isNullOrBlank()) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "Не передан trackId"),
            )
        }

        val entryPoint = AudioFeatureWorkerEntryPoint.from(applicationContext)
        val downloaded = entryPoint.downloadRepository().getDownloadedTrack(trackId)
        if (downloaded == null) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "Скачанный файл не найден"),
            )
        }

        val filePath = Uri.parse(downloaded.mediaUrl).path
        val file = filePath?.let(::File)
        if (file == null || !file.isFile) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "Некорректный локальный путь"),
            )
        }

        try {
            val pcm = entryPoint.audioFeatureExtractor().extractPcm(file)
            if (pcm.isEmpty()) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "Пустой PCM после декодирования"),
                )
            }

            val vector = entryPoint.audioEmbeddingEngine().embed(pcm)
            entryPoint.embeddingRepository().saveEmbedding(trackId, vector)

            Log.d(
                TAG,
                "Эмбеддинг сохранён: trackId=$trackId, размер=${vector.size}, " +
                    "PCM=${pcm.size}",
            )
            Result.success(workDataOf(KEY_EMBEDDING_SIZE to vector.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось посчитать эмбеддинг для $trackId", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Ошибка аудио-анализа")))
        }
    }

    companion object {
        const val KEY_TRACK_ID = "audio_track_id"
        const val KEY_EMBEDDING_SIZE = "embedding_size"
        const val KEY_ERROR = "audio_error"
        const val TAG_AUDIO_FEATURE = "audio-feature"

        private const val TAG = "AudioFeatureWorker"
    }
}
