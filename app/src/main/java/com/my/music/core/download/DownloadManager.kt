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
import com.my.music.core.audio.AudioFeatureWorker
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
 * и объединяет состояние Room и WorkInfo.
 *
 * WorkInfo — главный источник состояния задачи: если воркер отменён или
 * упал, UI обязан это показать. Прогресс приходит из `setProgress()` воркера,
 * поэтому отдельный Media3 DownloadManager больше не нужен.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
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

        // Второй шаг цепочки читает уже сохранённый файл из DownloadRepository,
        // поэтому в input передаём только trackId. Цепочка не запустит анализ,
        // если скачивание завершилось ошибкой.
        val featureRequest = OneTimeWorkRequestBuilder<AudioFeatureWorker>()
            .setInputData(
                workDataOf(AudioFeatureWorker.KEY_TRACK_ID to track.id),
            )
            .addTag(AudioFeatureWorker.TAG_AUDIO_FEATURE)
            .build()

        Log.d(
            TAG,
            "download(): trackId=${track.id}, workId=${request.id}, " +
                "audioWorkId=${featureRequest.id}, policy=REPLACE",
        )
        workManager.beginUniqueWork(
            uniqueWorkName(track.id),
            ExistingWorkPolicy.REPLACE,
            request,
        ).then(featureRequest).enqueue()
    }

    /**
     * Отменяет активное скачивание.
     *
     * Недокачанный `.part`-файл удаляет сам воркер при CancellationException.
     * Готовый файл и запись в Room не трогаем.
     */
    fun cancel(trackId: String) {
        Log.d(TAG, "cancel(): отменяем задачу скачивания $trackId")
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
    }

    /**
     * Наблюдает состояние скачивания конкретного трека:
     * запись в Room + статус и прогресс задачи WorkManager.
     */
    fun observeState(trackId: String): Flow<DownloadState> {
        return combine(
            downloadRepository.observeIsDownloaded(trackId),
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(trackId)),
        ) { isDownloaded, workInfos ->
            val workInfo = latestWorkInfo(workInfos)
            val workProgress = workInfo
                ?.progress
                ?.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f)
                ?: 0f

            resolveDownloadState(
                isDownloaded = isDownloaded,
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
         * 3. активный WorkInfo — прогресс из `setProgress()` воркера;
         * 4. без активной задачи считаем, что трек не скачан.
         */
        internal fun resolveDownloadState(
            isDownloaded: Boolean,
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

                workState != null && workState in ACTIVE_WORK_STATES ->
                    DownloadState.InProgress(safeProgress)

                else -> DownloadState.NotDownloaded
            }
        }
    }
}
