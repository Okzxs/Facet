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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {

    fun soundCloudRepository(): SoundCloudRepository

    fun youTubeRepository(): YouTubeRepository

    fun downloadRepository(): DownloadRepository

    fun downloadSourceResolver(): DownloadSourceResolver

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
