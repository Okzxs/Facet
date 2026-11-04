package com.facet.music.core.di

import com.facet.music.core.source.PlayableSource
import com.facet.music.core.source.SoundCloudPlayableSource
import com.facet.music.core.source.YouTubePlayableSource
import com.facet.music.data.source.external.SpotifyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Источники с собственным поиском и потоком.
 *
 * Deezer остаётся справочником метаданных, Apple удалён. YouTube и SoundCloud
 * отдают полный поток, Spotify — только метаданные; для metadata-only
 * источников работает внешний матч [com.facet.music.core.source.PlaybackResolver].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MusicSourceModule {

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSpotifySource(provider: SpotifyProvider): PlayableSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindYouTubeSource(source: YouTubePlayableSource): PlayableSource

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindSoundCloudSource(source: SoundCloudPlayableSource): PlayableSource
}
