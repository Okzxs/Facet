package com.facet.music.core.cache

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.facet.music.core.source.StreamResolver
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AudioCacheRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Кэш прослушанного: после 20 секунд воспроизведения трек докачивается
 * в приватный кэш приложения, чтобы играть его без сети.
 *
 * Источник и трансляция резолвятся заново: ссылки SoundCloud/YouTube живут
 * минуты, поэтому сохранённый при старте URL был бы мёртвым. HLS не кэшируем —
 * это набор сегментов, а не один файл.
 */
class AudioCacheWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val track = readTrack() ?: return Result.failure()
        if (track.source == Source.LOCAL) return Result.failure()

        val entryPoint = AudioCacheEntryPoint.from(applicationContext)
        val repository = entryPoint.audioCacheRepository()

        // Файл уже есть — работа не нужна.
        if (repository.fileFor(track.id) != null) return Result.success()

        val resolved = entryPoint.streamResolver().resolve(track).getOrElse { error ->
            Log.w(TAG, "Не удалось резолвить ${track.id}", error)
            return Result.retry()
        }
        if (resolved.isHls) {
            Log.d(TAG, "HLS не кэшируем: ${track.id}")
            return Result.failure()
        }

        return try {
            val file = withContext(Dispatchers.IO) {
                download(entryPoint.okHttpClient(), resolved.url, repository.fileForWrite(track.id))
            }
            repository.put(track.id, file)
            repository.enforceLimit()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось скачать в кэш ${track.id}", e)
            Result.retry()
        }
    }

    private fun download(client: OkHttpClient, url: String, target: File): File {
        val part = File(target.parentFile, target.name + PART_SUFFIX)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Пустой ответ")
            part.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return target
    }

    private fun readTrack(): Track? {
        val id = inputData.getString(KEY_ID)?.takeIf { it.isNotBlank() } ?: return null
        val source = inputData.getString(KEY_SOURCE)
            ?.let { runCatching { Source.valueOf(it) }.getOrNull() }
            ?: return null
        return Track(
            id = id,
            source = source,
            title = inputData.getString(KEY_TITLE).orEmpty(),
            artist = inputData.getString(KEY_ARTIST).orEmpty(),
            artworkUrl = inputData.getString(KEY_ARTWORK),
            streamUrl = inputData.getString(KEY_STREAM_URL),
            durationMs = inputData.getLong(KEY_DURATION_MS, 0L),
            genre = null,
            transcodingUrl = inputData.getString(KEY_TRANSCODING_URL),
        )
    }

    companion object {
        const val TAG = "AudioCacheWorker"

        const val KEY_ID = "id"
        const val KEY_SOURCE = "source"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ARTWORK = "artwork"
        const val KEY_STREAM_URL = "streamUrl"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_TRANSCODING_URL = "transcodingUrl"

        private const val PART_SUFFIX = ".part"
        private const val USER_AGENT = "Facet/1.0"
    }
}

/**
 * EntryPoint: WorkManager создаёт воркер сам, поэтому зависимости берём
 * из Hilt-графа, а не через конструктор.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioCacheEntryPoint {

    fun streamResolver(): StreamResolver

    fun audioCacheRepository(): AudioCacheRepository

    fun okHttpClient(): OkHttpClient

    companion object {
        fun from(context: Context): AudioCacheEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AudioCacheEntryPoint::class.java,
            )
    }
}
