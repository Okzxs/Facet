package com.my.music.core.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над WorkManager для UI: ставит задачи скачивания
 * и объединяет состояние Room, WorkInfo и Media3 DownloadManager.
 *
 * WorkInfo — главный источник состояния задачи: если воркер отменён или
 * упал, UI обязан это показать, даже если Media3 оставил старую запись
 * в индексе (иначе кнопка навсегда залипает на «загрузка»).
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /** Время последнего клика по кнопке скачивания для защиты от двойного тапа. */
    private val lastTapAt = mutableMapOf<String, Long>()

    /**
     * Ставит трек в очередь на скачивание.
     *
     * [ExistingWorkPolicy.REPLACE] гарантирует, что после отмены/падения
     * задача создаётся заново, а не игнорируется старой цепочкой unique work.
     * Повторный клик в течение [TAP_DEBOUNCE_MS] игнорируется, чтобы два
     * быстрых тапа не отменяли только что созданную задачу.
     */
    fun download(track: Track) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastTapAt[track.id]
        if (previous != null && now - previous < TAP_DEBOUNCE_MS) {
            Log.d(TAG, "download(): повторный клик проигнорирован для ${track.id}")
            return
        }
        lastTapAt[track.id] = now

        // Тег попытки позволяет выбрать самую свежую задачу из цепочки unique work.
        val attemptTag = ATTEMPT_TAG_PREFIX + System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<DownloadTrackWorker>()
            .setInputData(
                workDataOf(
                    DownloadTrackWorker.KEY_TRACK_ID to track.id,
                    DownloadTrackWorker.KEY_TITLE to track.title,
                    DownloadTrackWorker.KEY_ARTIST to track.artist,
                    DownloadTrackWorker.KEY_ARTWORK_URL to track.artworkUrl,
                    DownloadTrackWorker.KEY_DURATION_MS to track.durationMs,
                    DownloadTrackWorker.KEY_SOURCE to track.source.name,
                    DownloadTrackWorker.KEY_STREAM_URL to track.streamUrl,
                    DownloadTrackWorker.KEY_TRANSCODING_URL to track.transcodingUrl,
                    DownloadTrackWorker.KEY_FORCE_RESTART to true,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG_DOWNLOAD)
            .addTag(attemptTag)
            .build()

        Log.d(
            TAG,
            "download(): trackId=${track.id}, workId=${request.id}, policy=REPLACE",
        )
        workManager.enqueueUniqueWork(
            uniqueWorkName(track.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Отменяет активное скачивание и сразу убирает запись Media3.
     *
     * Важно: чистим кэш именно здесь, а не в catch воркера. При REPLACE
     * старый воркер тоже отменяется, и его очистка удалила бы загрузку
     * новой попытки.
     */
    fun cancel(trackId: String) {
        Log.d(TAG, "cancel(): отменяем задачу и чистим Media3 для $trackId")
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
        offlineDownloadManager.remove(trackId)
    }

    /**
     * Наблюдает состояние скачивания конкретного трека:
     * запись в Room + прогресс Media3 + статус задачи WorkManager.
     */
    fun observeState(trackId: String): Flow<DownloadState> {
        return combine(
            downloadRepository.observeIsDownloaded(trackId),
            offlineDownloadManager.observeDownload(trackId),
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(trackId)),
        ) { isDownloaded, mediaState, workInfos ->
            val workInfo = latestWorkInfo(workInfos)
            val workProgress = workInfo
                ?.progress
                ?.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f)
                ?: 0f

            resolveDownloadState(
                isDownloaded = isDownloaded,
                mediaState = mediaState,
                workState = workInfo?.state,
                workProgress = workProgress,
            )
        }.distinctUntilChanged()
    }

    /**
     * Выбирает последнюю попытку по тегу `download-at:<millis>`.
     * Порядок списка из WorkManager не гарантирован, поэтому опираться
     * на `lastOrNull()` нельзя.
     */
    private fun latestWorkInfo(workInfos: List<WorkInfo>): WorkInfo? {
        return workInfos.maxByOrNull { info ->
            info.tags.firstOrNull { it.startsWith(ATTEMPT_TAG_PREFIX) }
                ?.substring(ATTEMPT_TAG_PREFIX.length)
                ?.toLongOrNull()
                ?: 0L
        }
    }

    private fun uniqueWorkName(trackId: String): String = "download:$trackId"

    companion object {
        private const val TAG = "DownloadFlow"

        /** Тег всех задач скачивания — для диагностики и массовой отмены. */
        const val TAG_DOWNLOAD = "download"

        /** Префикс тега попытки: по нему выбирается самая свежая задача. */
        const val ATTEMPT_TAG_PREFIX = "download-at:"

        /** Защита от двойного тапа по кнопке скачивания. */
        private const val TAP_DEBOUNCE_MS = 700L

        private val ACTIVE_WORK_STATES = setOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING,
        )

        /**
         * Чистая функция сопоставления состояния для UI.
         *
         * Приоритет:
         * 1. запись в Room — трек точно доступен офлайн;
         * 2. терминальный WorkInfo (SUCCEEDED/CANCELLED/FAILED);
         * 3. активный WorkInfo + прогресс Media3;
         * 4. без активной задачи прогресс Media3 не показываем:
         *    осиротевшая запись иначе залипает навсегда.
         */
        internal fun resolveDownloadState(
            isDownloaded: Boolean,
            mediaState: MediaDownloadState,
            workState: WorkInfo.State?,
            workProgress: Float,
        ): DownloadState {
            val safeProgress = workProgress.coerceIn(0f, 1f)
            return when {
                isDownloaded -> DownloadState.Downloaded
                workState == WorkInfo.State.SUCCEEDED -> DownloadState.Downloaded
                workState == WorkInfo.State.CANCELLED ->
                    DownloadState.Error(DownloadState.Cause.CANCELLED)

                workState == WorkInfo.State.FAILED ->
                    DownloadState.Error(DownloadState.Cause.FAILED)

                workState != null && workState in ACTIVE_WORK_STATES -> when (mediaState) {
                    is MediaDownloadState.InProgress ->
                        DownloadState.InProgress(mediaState.progress.coerceIn(0f, 1f))

                    is MediaDownloadState.Completed -> DownloadState.Downloaded
                    is MediaDownloadState.Failed ->
                        DownloadState.Error(DownloadState.Cause.FAILED)

                    is MediaDownloadState.Queued, MediaDownloadState.Missing ->
                        DownloadState.InProgress(safeProgress)
                }

                else -> DownloadState.NotDownloaded
            }
        }
    }
}
