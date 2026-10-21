package com.my.music.core.download

import android.content.Context
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
 * и объединяет состояние Room с реальным состоянием Media3 DownloadManager.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /**
     * Ставит трек в очередь на скачивание.
     * Повторный вызов для того же трека не создаёт дубликат, пока задача активна.
     */
    fun download(track: Track) {
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
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(track.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Отменяет активное скачивание. Недокачанные данные из кэша
     * удаляет сам воркер при отмене; готовый трек не трогаем.
     */
    fun cancel(trackId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
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
            val workInfo = activeWorkInfo(workInfos)
            val workProgress = workInfo
                ?.progress
                ?.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f)
                ?: 0f

            when {
                isDownloaded -> DownloadState.Downloaded
                mediaState is MediaDownloadState.InProgress ->
                    DownloadState.InProgress(mediaState.progress)

                mediaState is MediaDownloadState.Queued ->
                    DownloadState.InProgress(workProgress)

                mediaState is MediaDownloadState.Failed -> DownloadState.Failed
                workInfo?.state == WorkInfo.State.FAILED -> DownloadState.Failed
                // Media3 уже докачал, но Room-запись появится чуть позже
                mediaState is MediaDownloadState.Completed -> DownloadState.Downloaded
                workInfo != null && workInfo.state in ACTIVE_WORK_STATES ->
                    DownloadState.InProgress(workProgress)

                else -> DownloadState.NotDownloaded
            }
        }.distinctUntilChanged()
    }

    /** Активная задача важнее старых записей в цепочке unique work. */
    private fun activeWorkInfo(workInfos: List<WorkInfo>): WorkInfo? {
        return workInfos.lastOrNull { it.state == WorkInfo.State.RUNNING }
            ?: workInfos.lastOrNull {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
            }
            ?: workInfos.lastOrNull()
    }

    private fun uniqueWorkName(trackId: String): String = "download:$trackId"

    companion object {
        /** Тег всех задач скачивания — для диагностики и массовой отмены. */
        const val TAG_DOWNLOAD = "download"

        private val ACTIVE_WORK_STATES = setOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING,
        )
    }
}
