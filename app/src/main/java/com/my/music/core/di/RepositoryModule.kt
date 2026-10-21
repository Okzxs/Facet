package com.my.music.core.di

import com.my.music.core.location.FusedLocationProvider
import com.my.music.core.location.LocationProvider
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.source.soundcloud.SoundCloudRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSoundCloudRepository(
        impl: SoundCloudRepositoryImpl,
    ): SoundCloudRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(
        impl: com.my.music.data.source.lyrics.LyricsRepositoryImpl,
    ): com.my.music.data.repository.LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLikedTracksRepository(
        impl: com.my.music.data.local.LikedTracksRepositoryImpl,
    ): com.my.music.data.repository.LikedTracksRepository

    @Binds
    @Singleton
    abstract fun bindPlayHistoryRepository(
        impl: com.my.music.data.local.PlayHistoryRepositoryImpl,
    ): com.my.music.data.repository.PlayHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSoundCloudChartsRepository(
        impl: com.my.music.data.source.soundcloud.SoundCloudChartsRepositoryImpl,
    ): com.my.music.data.repository.SoundCloudChartsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: com.my.music.data.local.SettingsRepositoryImpl,
    ): com.my.music.data.repository.SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(
        impl: FusedLocationProvider,
    ): LocationProvider

    @Binds
    @Singleton
    abstract fun bindWaveRepository(
        impl: com.my.music.data.source.wave.WaveRepositoryImpl,
    ): com.my.music.data.repository.WaveRepository

    @Binds
    @Singleton
    abstract fun bindTasteRepository(
        impl: com.my.music.data.local.TasteRepositoryImpl,
    ): com.my.music.data.repository.TasteRepository

    @Binds
    @Singleton
    abstract fun bindLastFmRepository(
        impl: com.my.music.data.source.lastfm.LastFmRepositoryImpl,
    ): com.my.music.data.repository.LastFmRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: com.my.music.data.local.DownloadRepositoryImpl,
    ): com.my.music.data.repository.DownloadRepository
}

