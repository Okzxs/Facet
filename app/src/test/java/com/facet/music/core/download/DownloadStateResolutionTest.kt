package com.facet.music.core.download

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStateResolutionTest {

    @Test
    fun `room record has the highest priority`() {
        assertEquals(
            DownloadState.Downloaded,
            resolve(
                isDownloaded = true,
                workState = WorkInfo.State.RUNNING,
                workProgress = 0.2f,
            ),
        )
    }

    @Test
    fun `succeeded work shows checkmark`() {
        assertEquals(
            DownloadState.Downloaded,
            resolve(workState = WorkInfo.State.SUCCEEDED),
        )
    }

    @Test
    fun `cancelled work shows error even with progress`() {
        assertEquals(
            DownloadState.Error(DownloadState.Cause.CANCELLED),
            resolve(
                workState = WorkInfo.State.CANCELLED,
                workProgress = 0.8f,
            ),
        )
    }

    @Test
    fun `failed work shows error`() {
        assertEquals(
            DownloadState.Error(DownloadState.Cause.FAILED),
            resolve(workState = WorkInfo.State.FAILED),
        )
    }

    @Test
    fun `running work uses worker progress`() {
        assertEquals(
            DownloadState.InProgress(0.35f),
            resolve(
                workState = WorkInfo.State.RUNNING,
                workProgress = 0.35f,
            ),
        )
    }

    @Test
    fun `enqueued work shows indeterminate progress`() {
        assertEquals(
            DownloadState.InProgress(0f),
            resolve(workState = WorkInfo.State.ENQUEUED),
        )
    }

    @Test
    fun `blocked work still shows progress`() {
        assertEquals(
            DownloadState.InProgress(0.1f),
            resolve(
                workState = WorkInfo.State.BLOCKED,
                workProgress = 0.1f,
            ),
        )
    }

    @Test
    fun `no work means track is not downloaded`() {
        assertEquals(
            DownloadState.NotDownloaded,
            resolve(workState = null),
        )
    }

    @Test
    fun `progress above one is clamped`() {
        assertEquals(
            DownloadState.InProgress(1f),
            resolve(
                workState = WorkInfo.State.RUNNING,
                workProgress = 4.2f,
            ),
        )
    }

    @Test
    fun `negative progress is clamped to zero`() {
        assertEquals(
            DownloadState.InProgress(0f),
            resolve(
                workState = WorkInfo.State.RUNNING,
                workProgress = -0.5f,
            ),
        )
    }

    private fun resolve(
        isDownloaded: Boolean = false,
        workState: WorkInfo.State?,
        workProgress: Float = 0f,
    ): DownloadState = DownloadManager.resolveDownloadState(
        isDownloaded = isDownloaded,
        workState = workState,
        workProgress = workProgress,
    )
}
