package com.my.music.core.di

import com.my.music.core.download.DownloadSource
import com.my.music.core.download.SoundCloudDownloadSource
import com.my.music.core.download.YouTubeDownloadSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Набор источников для [com.my.music.core.download.DownloadSourceResolver].
 * Новый источник достаточно добавить отдельным `@IntoSet`-биндингом.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadSourceModule {

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindYouTubeDownloadSource(
        impl: YouTubeDownloadSource,
    ): DownloadSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSoundCloudDownloadSource(
        impl: SoundCloudDownloadSource,
    ): DownloadSource
}
