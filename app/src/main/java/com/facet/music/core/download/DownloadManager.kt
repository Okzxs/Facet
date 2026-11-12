package com.facet.music.core.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private val lastTapAt = ConcurrentHashMap<String, Long>()

    suspend fun download(track: Track): Result<Unit> {
        val now = SystemClock.elapsedRealtime()
        lastTapAt.entries.removeAll { now - it.value > TAP_HISTORY_TTL_MS }
        val previous = lastTapAt[track.id]
        if (previous != null && now - previous < TAP_DEBOUNCE_MS) {
            Log.d(TAG, "download(): повторный клик проигнорирован для ${track.id}")
            return Result.success(Unit)
        }
        lastTapAt[track.id] = now

        if (observeState(track.id).first() is DownloadState.InProgress) {
            Log.d(TAG, "download(): скачивание уже идёт для ${track.id}")
            return Result.success(Unit)
        }

        return try {
            enqueue(track)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "download(): не удалось поставить задачу для ${track.id}", e)
            Result.failure(e)
        }
    }

    fun cancel(trackId: String) {
        Log.d(TAG, "cancel(): отменяем задачу скачивания $trackId")
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
    }

    fun observeState(trackId: String): Flow<DownloadState> =
        observeStates(listOf(trackId)).map { states ->
            states[trackId] ?: DownloadState.NotDownloaded
        }

    fun observeStates(trackIds: Collection<String>): Flow<Map<String, DownloadState>> {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())

        return combine(
            downloadRepository.observeDownloadedIds(),
            workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD),
        ) { downloaded, workInfos ->
            val latestByTrack = workInfos
                .mapNotNull { info ->
                    trackIdOf(info)?.let { trackId -> trackId to info }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, infos) -> latestWorkInfo(infos) }

            ids.associateWith { trackId ->
                val workInfo = latestByTrack[trackId]
                val progress = workInfo
                    ?.progress
                    ?.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f)
                    ?: 0f
                resolveDownloadState(
                    isDownloaded = trackId in downloaded,
                    workState = workInfo?.state,
                    workProgress = progress,
                    workError = workInfo?.outputData?.getString(DownloadTrackWorker.KEY_ERROR),
                )
            }
        }.distinctUntilChanged()
    }

    private fun enqueue(track: Track) {
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
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(TAG_DOWNLOAD)
            .addTag(trackTag(track.id))
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

    private fun latestWorkInfo(workInfos: List<WorkInfo>): WorkInfo? {
        return workInfos.maxByOrNull { info ->
            info.tags.firstOrNull { it.startsWith(ATTEMPT_TAG_PREFIX) }
                ?.substring(ATTEMPT_TAG_PREFIX.length)
                ?.toLongOrNull()
                ?: 0L
        }
    }

    private fun trackIdOf(workInfo: WorkInfo): String? =
        workInfo.tags
            .firstOrNull { it.startsWith(TRACK_TAG_PREFIX) }
            ?.removePrefix(TRACK_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }

    private fun uniqueWorkName(trackId: String): String = "download:$trackId"

    private fun trackTag(trackId: String): String = TRACK_TAG_PREFIX + trackId

    companion object {
        private const val TAG = "DownloadFlow"

        const val TAG_DOWNLOAD = "download"

        const val TRACK_TAG_PREFIX = "download-track:"

        const val ATTEMPT_TAG_PREFIX = "download-at:"

        private const val TAP_DEBOUNCE_MS = 700L

        private const val TAP_HISTORY_TTL_MS = 60_000L

        private const val RETRY_BACKOFF_SECONDS = 30L

        private val ACTIVE_WORK_STATES = setOf(
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.RUNNING,
        )

        internal fun resolveDownloadState(
            isDownloaded: Boolean,
            workState: WorkInfo.State?,
            workProgress: Float,
            workError: String? = null,
        ): DownloadState {
            val safeProgress = workProgress.coerceIn(0f, 1f)
            return when {
                isDownloaded -> DownloadState.Downloaded
                workState == WorkInfo.State.SUCCEEDED -> DownloadState.Downloaded
                workState == WorkInfo.State.CANCELLED ->
                    DownloadState.Error(DownloadState.Cause.CANCELLED)

                workState == WorkInfo.State.FAILED ->
                    DownloadState.Error(DownloadState.Cause.FAILED, workError)

                workState != null && workState in ACTIVE_WORK_STATES ->
                    DownloadState.InProgress(safeProgress)

                else -> DownloadState.NotDownloaded
            }
        }
    }
}
