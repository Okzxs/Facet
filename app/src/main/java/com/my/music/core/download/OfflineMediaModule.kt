package com.my.music.core.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Hilt-граф офлайн-режима на Media3.
 *
 * Кэш [SimpleCache] обязан быть один на процесс: и загрузчик, и плеер
 * обращаются к одному каталогу `filesDir/media_cache`. В Media3 класс
 * `ExoDatabaseProvider` из ExoPlayer 2 называется [StandaloneDatabaseProvider].
 */
@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object OfflineMediaModule {

    /** Общий источник данных: сеть с корректным User-Agent SoundCloud. */
    @Provides
    @Singleton
    fun provideUpstreamDataSourceFactory(
        @ApplicationContext context: Context,
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(OfflineDownloadManager.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        return DefaultDataSource.Factory(context, httpFactory)
    }

    @Provides
    @Singleton
    fun provideMediaDatabase(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideMediaCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache = SimpleCache(
        File(context.filesDir, OfflineDownloadManager.CACHE_DIR),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    /**
     * Источник для воспроизведения: сначала общий кэш, при промахе — сеть.
     * Тот же кэш наполняет Media3 DownloadManager, поэтому скачанные
     * HLS-сегменты и MP3 играют без интернета.
     */
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: SimpleCache,
        upstreamFactory: DataSource.Factory,
    ): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Provides
    @Singleton
    fun provideMedia3DownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: SimpleCache,
        upstreamFactory: DataSource.Factory,
    ): DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        upstreamFactory,
        Executors.newSingleThreadExecutor(),
    )
}
