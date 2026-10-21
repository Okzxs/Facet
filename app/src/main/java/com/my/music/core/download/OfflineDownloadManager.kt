package com.my.music.core.download

import android.net.Uri
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Media3 [DownloadManager].
 *
 * Зачем нужен отдельный класс: Media3 умеет скачивать не только прямой MP3,
 * но и HLS-потоки (m3u8 + сегменты). Сегменты складываются в общий
 * [androidx.media3.datasource.cache.SimpleCache], поэтому отдельного
 * «одного файла» у HLS-трека нет — воспроизведение идёт из кэша.
 *
 * Здесь же живёт подписка на прогресс через [DownloadManager.Listener]:
 * UI получает состояние напрямую от Media3, а не из WorkManager.
 */
@UnstableApi
@Singleton
class OfflineDownloadManager @Inject constructor(
    private val downloadManager: DownloadManager,
) {

    /**
     * Ставит трек в очередь Media3, если он ещё не скачан и не качается.
     * Повторный вызов безопасен: незавершённую/проваленную загрузку Media3
     * перезапустит, а готовую — пропустит.
     */
    suspend fun enqueue(track: Track, stream: AudioStreamResult) {
        if (track.id.isBlank() || stream.url.isBlank()) return
        withContext(Dispatchers.IO) {
            val existing = loadDownload(track.id)
            if (existing != null && existing.state in ACTIVE_OR_DONE_STATES) return@withContext

            val request = DownloadRequest.Builder(track.id, Uri.parse(stream.url))
                .setMimeType(mimeTypeOf(stream))
                .build()
            downloadManager.addDownload(request)
        }
    }

    /** Скачан ли трек полностью (по индексу Media3, а не по Room). */
    suspend fun isCompleted(trackId: String): Boolean = withContext(Dispatchers.IO) {
        loadDownload(trackId)?.state == Download.STATE_COMPLETED
    }

    /**
     * Ждёт завершения загрузки и сообщает прогресс.
     *
     * Media3 не даёт блокирующего API, поэтому воркер опрашивает
     * DownloadIndex. Для UI используется событийный [observeDownload].
     */
    suspend fun awaitCompletion(
        trackId: String,
        onProgress: suspend (Float) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        var download = loadDownload(trackId)
        val deadline = System.currentTimeMillis() + APPEAR_TIMEOUT_MS
        while (download == null && System.currentTimeMillis() < deadline) {
            delay(APPEAR_POLL_MS)
            download = loadDownload(trackId)
        }
        if (download == null) {
            return@withContext DownloadOutcome.Failed("Media3 не поставил загрузку в очередь")
        }

        while (true) {
            currentCoroutineContext().ensureActive()
            val current = download
                ?: return@withContext DownloadOutcome.Failed("Загрузка пропала из индекса")
            when (current.state) {
                Download.STATE_COMPLETED -> return@withContext DownloadOutcome.Completed
                Download.STATE_FAILED -> return@withContext DownloadOutcome.Failed(
                    "failureReason=${current.failureReason}",
                )

                Download.STATE_REMOVING ->
                    return@withContext DownloadOutcome.Failed("Загрузка удалена")

                else -> {
                    val percent = current.percentDownloaded
                    if (percent.isFinite() && percent >= 0f) {
                        onProgress((percent / 100f).coerceIn(0f, 1f))
                    }
                    delay(PROGRESS_POLL_MS)
                    download = loadDownload(trackId)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadOutcome.Failed("Неизвестное состояние загрузки")
    }

    /** Удаляет загрузку из индекса и связанные данные из кэша. */
    fun remove(trackId: String) {
        runCatching { downloadManager.removeDownload(trackId) }
            .onFailure { Log.e(TAG, "Не удалось удалить загрузку $trackId", it) }
    }

    /**
     * Наблюдает состояние конкретной загрузки.
     * Слушатель Media3 вызывается на главном looper, поэтому запрос в БД
     * для начального состояния выполняется на Dispatchers.IO.
     */
    fun observeDownload(trackId: String): Flow<MediaDownloadState> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                if (download.request.id == trackId) {
                    trySend(download.toMediaState(finalException))
                }
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download,
            ) {
                if (download.request.id == trackId) {
                    trySend(MediaDownloadState.Missing)
                }
            }
        }

        downloadManager.addListener(listener)
        try {
            trySend(
                withContext(Dispatchers.IO) {
                    loadDownload(trackId)?.toMediaState() ?: MediaDownloadState.Missing
                },
            )
            awaitClose()
        } finally {
            downloadManager.removeListener(listener)
        }
    }

    private fun loadDownload(trackId: String): Download? = try {
        downloadManager.downloadIndex.getDownload(trackId)
    } catch (e: IOException) {
        Log.e(TAG, "Не удалось прочитать индекс загрузок для $trackId", e)
        null
    }

    private fun Download.toMediaState(finalException: Exception? = null): MediaDownloadState {
        return when (state) {
            Download.STATE_COMPLETED -> MediaDownloadState.Completed
            Download.STATE_FAILED -> MediaDownloadState.Failed(
                finalException?.message ?: "failureReason=$failureReason",
            )

            Download.STATE_REMOVING -> MediaDownloadState.Missing
            Download.STATE_QUEUED,
            Download.STATE_STOPPED,
            Download.STATE_RESTARTING,
            -> MediaDownloadState.Queued

            Download.STATE_DOWNLOADING -> {
                val percent = percentDownloaded
                val progress = if (percent.isFinite() && percent >= 0f) {
                    (percent / 100f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                MediaDownloadState.InProgress(progress)
            }

            else -> MediaDownloadState.Missing
        }
    }

    /** Результат ожидания загрузки для воркера. */
    sealed interface DownloadOutcome {
        data object Completed : DownloadOutcome
        data class Failed(val reason: String?) : DownloadOutcome
    }

    companion object {
        private const val TAG = "OfflineDownload"

        /** Каталог SimpleCache во внутреннем хранилище приложения. */
        const val CACHE_DIR = "media_cache"

        /** User-Agent важен для CDN SoundCloud. */
        const val USER_AGENT = "MyMusicApp/1.0"

        private const val APPEAR_TIMEOUT_MS = 10_000L
        private const val APPEAR_POLL_MS = 200L
        private const val PROGRESS_POLL_MS = 500L

        private val ACTIVE_OR_DONE_STATES = setOf(
            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING,
            Download.STATE_COMPLETED,
        )

        /** MIME-тип для медиаэлемента и записи в Room. */
        fun mimeTypeOf(stream: AudioStreamResult): String =
            if (stream.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.AUDIO_MPEG
    }
}
