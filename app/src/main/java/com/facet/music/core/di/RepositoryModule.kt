package com.facet.music.core.di

import com.facet.music.core.location.FusedLocationProvider
import com.facet.music.core.location.LocationProvider
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.data.source.soundcloud.SoundCloudRepositoryImpl
import com.facet.music.data.source.youtube.YouTubeRepositoryImpl
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
    abstract fun bindYouTubeRepository(
        impl: YouTubeRepositoryImpl,
    ): YouTubeRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(
        impl: com.facet.music.data.source.lyrics.LyricsRepositoryImpl,
    ): com.facet.music.data.repository.LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLikedTracksRepository(
        impl: com.facet.music.data.local.LikedTracksRepositoryImpl,
    ): com.facet.music.data.repository.LikedTracksRepository

    @Binds
    @Singleton
    abstract fun bindPlayHistoryRepository(
        impl: com.facet.music.data.local.PlayHistoryRepositoryImpl,
    ): com.facet.music.data.repository.PlayHistoryRepository

    @Binds
    @Singleton
    abstract fun bindRecentlyShownRepository(
        impl: com.facet.music.data.local.RecentlyShownRepositoryImpl,
    ): com.facet.music.data.repository.RecentlyShownRepository

    @Binds
    @Singleton
    abstract fun bindSoundCloudChartsRepository(
        impl: com.facet.music.data.source.soundcloud.SoundCloudChartsRepositoryImpl,
    ): com.facet.music.data.repository.SoundCloudChartsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: com.facet.music.data.local.SettingsRepositoryImpl,
    ): com.facet.music.data.repository.SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLocationProvider(
        impl: FusedLocationProvider,
    ): LocationProvider

    @Binds
    @Singleton
    abstract fun bindWaveRepository(
        impl: com.facet.music.data.source.wave.WaveRepositoryImpl,
    ): com.facet.music.data.repository.WaveRepository

    @Binds
    @Singleton
    abstract fun bindTasteRepository(
        impl: com.facet.music.data.local.TasteRepositoryImpl,
    ): com.facet.music.data.repository.TasteRepository

    @Binds
    @Singleton
    abstract fun bindLastFmRepository(
        impl: com.facet.music.data.source.lastfm.LastFmRepositoryImpl,
    ): com.facet.music.data.repository.LastFmRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: com.facet.music.data.local.DownloadRepositoryImpl,
    ): com.facet.music.data.repository.DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSearchCacheRepository(
        impl: com.facet.music.data.local.SearchCacheRepositoryImpl,
    ): com.facet.music.data.repository.SearchCacheRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        impl: com.facet.music.data.local.PlaylistRepositoryImpl,
    ): com.facet.music.data.repository.PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistImportService(
        impl: com.facet.music.data.source.imports.PlaylistImportServiceImpl,
    ): com.facet.music.data.source.imports.PlaylistImportService

    @Binds
    @Singleton
    abstract fun bindSpotifyAuthRepository(
        impl: com.facet.music.data.source.external.SpotifyAuthRepositoryImpl,
    ): com.facet.music.data.repository.SpotifyAuthRepository

    @Binds
    @Singleton
    abstract fun bindQueueRepository(
        impl: com.facet.music.data.local.QueueRepositoryImpl,
    ): com.facet.music.data.repository.QueueRepository
}

