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
 * Обёртка над WorkManager: ставит задачи скачивания и отдаёт их состояние в UI.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
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

    /** Отменяет активное скачивание (уже готовый файл не трогаем). */
    fun cancel(trackId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
    }

    /**
     * Наблюдает состояние скачивания конкретного трека:
     * запись в Room + статус задачи WorkManager.
     */
    fun observeState(trackId: String): Flow<DownloadState> {
        return combine(
            downloadRepository.observeIsDownloaded(trackId),
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(trackId)),
        ) { isDownloaded, workInfos ->
            if (isDownloaded) {
                DownloadState.Downloaded
            } else {
                when (val info = workInfos.lastOrNull()) {
                    null -> DownloadState.NotDownloaded
                    else -> when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED,
                        WorkInfo.State.RUNNING,
                        -> DownloadState.InProgress(
                            info.progress.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f),
                        )

                        WorkInfo.State.SUCCEEDED -> DownloadState.Downloaded
                        WorkInfo.State.FAILED -> DownloadState.Failed
                        WorkInfo.State.CANCELLED -> DownloadState.NotDownloaded
                    }
                }
            }
        }.distinctUntilChanged()
    }

    private fun uniqueWorkName(trackId: String): String = "download:$trackId"

    companion object {
        /** Тег всех задач скачивания — для диагностики и массовой отмены. */
        const val TAG_DOWNLOAD = "download"
    }
}
