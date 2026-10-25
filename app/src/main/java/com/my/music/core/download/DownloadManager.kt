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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над WorkManager для UI: выбирает источник, ставит задачу скачивания
 * и объединяет состояние Room и WorkInfo.
 *
 * Резолвер вызывается до воркера: воркер получает уже готовый прямой
 * progressive-URL. Так пользователю не нужно выбирать источник вручную,
 * а HLS-треки SoundCloud автоматически скачиваются с YouTube.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val sourceResolver: DownloadSourceResolver,
) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /** Время последнего клика по кнопке скачивания для защиты от двойного тапа. */
    private val lastTapAt = mutableMapOf<String, Long>()

    /** Треки, для которых сейчас идёт выбор источника (до появления WorkInfo). */
    private val resolvingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Активные корутины резолва — отмена кнопкой должна прервать и поиск. */
    private val resolveJobs = ConcurrentHashMap<String, Job>()

    /**
     * Ставит трек в очередь на скачивание.
     *
     * Сначала [DownloadSourceResolver] находит источник, который отдаёт
     * progressive-файл. Если источника нет, возвращается ошибка — воркер
     * не создаётся, а UI показывает Snackbar.
     */
    suspend fun download(track: Track): Result<Unit> {
        val now = SystemClock.elapsedRealtime()
        val previous = lastTapAt[track.id]
        if (previous != null && now - previous < TAP_DEBOUNCE_MS) {
            Log.d(TAG, "download(): повторный клик проигнорирован для ${track.id}")
            return Result.success(Unit)
        }
        lastTapAt[track.id] = now

        val job = currentCoroutineContext()[Job]
        if (job != null) resolveJobs[track.id] = job
        resolvingIds.update { it + track.id }

        return try {
            sourceResolver.resolve(track).fold(
                onSuccess = { resolved ->
                    enqueue(track, resolved)
                    Log.d(
                        TAG,
                        "download(): ${track.id} → ${resolved.source}, url=${resolved.streamUrl.take(80)}",
                    )
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.w(TAG, "download(): источник не найден для ${track.id}: ${error.message}")
                    Result.failure(error)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "download(): ошибка выбора источника для ${track.id}", e)
            Result.failure(e)
        } finally {
            resolvingIds.update { it - track.id }
            if (job != null && resolveJobs[track.id] === job) {
                resolveJobs.remove(track.id)
            }
        }
    }

    /**
     * Отменяет активное скачивание и выбор источника.
     *
     * Недокачанный `.part`-файл удаляет сам воркер при CancellationException.
     * Готовый файл и запись в Room не трогаем.
     */
    fun cancel(trackId: String) {
        Log.d(TAG, "cancel(): отменяем задачу скачивания $trackId")
        resolveJobs.remove(trackId)?.cancel()
        workManager.cancelUniqueWork(uniqueWorkName(trackId))
    }

    /**
     * Наблюдает состояние скачивания конкретного трека:
     * запись в Room + статус и прогресс задачи WorkManager.
     */
    fun observeState(trackId: String): Flow<DownloadState> =
        observeStates(listOf(trackId)).map { states ->
            states[trackId] ?: DownloadState.NotDownloaded
        }

    /**
     * Состояния сразу для списка треков — один WorkManager-запрос на экран.
     * Нужно карточкам «Для вас», «Недавнего», «Воспоминаний» и медиатеки.
     */
    fun observeStates(trackIds: Collection<String>): Flow<Map<String, DownloadState>> {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())

        return combine(
            downloadRepository.observeDownloadedIds(),
            workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD),
            resolvingIds,
        ) { downloaded, workInfos, resolving ->
            val latestByTrack = workInfos
                .mapNotNull { info ->
                    trackIdOf(info)?.let { trackId -> trackId to info }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, infos) -> latestWorkInfo(infos) }

            ids.associateWith { trackId ->
                if (trackId in resolving) {
                    DownloadState.InProgress(0f)
                } else {
                    val workInfo = latestByTrack[trackId]
                    val progress = workInfo
                        ?.progress
                        ?.getFloat(DownloadTrackWorker.KEY_PROGRESS, 0f)
                        ?: 0f
                    resolveDownloadState(
                        isDownloaded = trackId in downloaded,
                        workState = workInfo?.state,
                        workProgress = progress,
                    )
                }
            }
        }.distinctUntilChanged()
    }

    private fun enqueue(track: Track, resolved: ResolvedDownload) {
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
                    DownloadTrackWorker.KEY_DIRECT_STREAM_URL to resolved.streamUrl,
                    DownloadTrackWorker.KEY_DIRECT_MIME_TYPE to resolved.mimeType,
                    DownloadTrackWorker.KEY_RESOLVED_SOURCE to resolved.source.name,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
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

    private fun trackIdOf(workInfo: WorkInfo): String? =
        workInfo.tags
            .firstOrNull { it.startsWith(TRACK_TAG_PREFIX) }
            ?.removePrefix(TRACK_TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }

    private fun uniqueWorkName(trackId: String): String = "download:$trackId"

    private fun trackTag(trackId: String): String = TRACK_TAG_PREFIX + trackId

    companion object {
        private const val TAG = "DownloadFlow"

        /** Тег всех задач скачивания — для диагностики и массовой отмены. */
        const val TAG_DOWNLOAD = "download"

        /** Префикс тега трека: по нему список экрана находит свой WorkInfo. */
        const val TRACK_TAG_PREFIX = "download-track:"

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
