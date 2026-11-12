package com.facet.music.core.download

sealed interface DownloadState {

    data object NotDownloaded : DownloadState

    data class InProgress(val progress: Float) : DownloadState

    data object Downloaded : DownloadState

    data class Error(
        val cause: Cause,
        val message: String? = null,
    ) : DownloadState

    enum class Cause {
        CANCELLED,
        FAILED,
    }
}
