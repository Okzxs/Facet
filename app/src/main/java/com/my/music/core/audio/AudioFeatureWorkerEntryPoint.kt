package com.my.music.core.audio

import android.content.Context
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.EmbeddingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Точка доступа к Hilt-графу из [AudioFeatureWorker].
 *
 * WorkManager создаёт воркер сам, а Hilt WorkManager в проекте не подключён,
 * поэтому зависимости получаем через EntryPoint.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioFeatureWorkerEntryPoint {

    fun downloadRepository(): DownloadRepository

    fun audioFeatureExtractor(): AudioFeatureExtractor

    fun audioEmbeddingEngine(): AudioEmbeddingEngine

    fun embeddingRepository(): EmbeddingRepository

    companion object {
        fun from(context: Context): AudioFeatureWorkerEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AudioFeatureWorkerEntryPoint::class.java,
            )
    }
}
