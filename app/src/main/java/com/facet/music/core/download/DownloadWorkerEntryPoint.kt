package com.facet.music.core.download

import android.content.Context
import com.facet.music.core.network.MediaClient
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

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

    /** Свежий progressive-URL резолвится внутри воркера перед скачиванием. */
    fun downloadSourceResolver(): DownloadSourceResolver

    /** Медиа-клиент: длинное чтение и отсутствие общего дедлайна. */
    @MediaClient
    fun mediaClient(): OkHttpClient

    companion object {
        fun from(context: Context): DownloadWorkerEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DownloadWorkerEntryPoint::class.java,
            )
    }
}
