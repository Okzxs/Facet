package com.my.music.core.download

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверяет приоритет источников состояния скачивания:
 * Room → терминальный WorkInfo → активный WorkInfo + Media3.
 */
class DownloadStateResolutionTest {

    @Test
    fun `room record has the highest priority`() {
        assertEquals(
            DownloadState.Downloaded,
            resolve(
                isDownloaded = true,
                mediaState = MediaDownloadState.InProgress(0.2f),
                workState = WorkInfo.State.RUNNING,
            ),
        )
    }

    @Test
    fun `succeeded work shows checkmark`() {
        assertEquals(
            DownloadState.Downloaded,
            resolve(
                mediaState = MediaDownloadState.Missing,
                workState = WorkInfo.State.SUCCEEDED,
            ),
        )
    }

    @Test
    fun `cancelled work overrides stale media progress`() {
        assertEquals(
            DownloadState.Error(DownloadState.Cause.CANCELLED),
            resolve(
                mediaState = MediaDownloadState.InProgress(0.8f),
                workState = WorkInfo.State.CANCELLED,
            ),
        )
    }

    @Test
    fun `failed work overrides queued media`() {
        assertEquals(
            DownloadState.Error(DownloadState.Cause.FAILED),
            resolve(
                mediaState = MediaDownloadState.Queued,
                workState = WorkInfo.State.FAILED,
            ),
        )
    }

    @Test
    fun `active work uses media progress`() {
        assertEquals(
            DownloadState.InProgress(0.35f),
            resolve(
                mediaState = MediaDownloadState.InProgress(0.35f),
                workState = WorkInfo.State.RUNNING,
            ),
        )
    }

    @Test
    fun `active work without media entry falls back to work progress`() {
        assertEquals(
            DownloadState.InProgress(0.1f),
            resolve(
                mediaState = MediaDownloadState.Missing,
                workState = WorkInfo.State.ENQUEUED,
                workProgress = 0.1f,
            ),
        )
    }

    @Test
    fun `completed media during active work shows checkmark`() {
        assertEquals(
            DownloadState.Downloaded,
            resolve(
                mediaState = MediaDownloadState.Completed,
                workState = WorkInfo.State.RUNNING,
            ),
        )
    }

    @Test
    fun `media failure during active work shows error`() {
        assertEquals(
            DownloadState.Error(DownloadState.Cause.FAILED),
            resolve(
                mediaState = MediaDownloadState.Failed("404"),
                workState = WorkInfo.State.RUNNING,
            ),
        )
    }

    @Test
    fun `orphaned media progress without work is not shown`() {
        assertEquals(
            DownloadState.NotDownloaded,
            resolve(
                mediaState = MediaDownloadState.InProgress(0.9f),
                workState = null,
            ),
        )
    }

    @Test
    fun `orphaned completed media without room is not treated as downloaded`() {
        assertEquals(
            DownloadState.NotDownloaded,
            resolve(
                mediaState = MediaDownloadState.Completed,
                workState = null,
            ),
        )
    }

    private fun resolve(
        isDownloaded: Boolean = false,
        mediaState: MediaDownloadState,
        workState: WorkInfo.State?,
        workProgress: Float = 0f,
    ): DownloadState = DownloadManager.resolveDownloadState(
        isDownloaded = isDownloaded,
        mediaState = mediaState,
        workState = workState,
        workProgress = workProgress,
    )
}
