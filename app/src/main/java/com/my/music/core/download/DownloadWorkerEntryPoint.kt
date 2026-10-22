package com.my.music.core.download

import android.content.Context
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.YouTubeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Точка доступа к графу Hilt из [DownloadTrackWorker].
 *
 * WorkManager создаёт воркер сам, поэтому зависимости берём через EntryPoint —
 * это избавляет от дополнительной зависимости androidx.hilt:hilt-work.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {

    fun soundCloudRepository(): SoundCloudRepository

    fun youTubeRepository(): YouTubeRepository

    fun downloadRepository(): DownloadRepository

    companion object {
        fun from(context: Context): DownloadWorkerEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DownloadWorkerEntryPoint::class.java,
            )
    }
}
