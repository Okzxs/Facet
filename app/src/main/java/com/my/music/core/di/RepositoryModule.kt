package com.my.music.core.di

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
}

