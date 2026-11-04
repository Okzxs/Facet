package com.facet.music.core.di

import com.facet.music.core.source.MusicSourceProvider
import com.facet.music.data.source.external.SpotifyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Внешние источники с собственным поиском.
 *
 * Deezer и Apple больше не источники воспроизведения: Deezer остаётся
 * справочником метаданных, Apple удалён. Единственный провайдер —
 * Spotify; SoundCloud и YouTube работают через свои репозитории.
 */
@Module
@InstallIn(SingletonComponent::class)
object MusicSourceModule {

    @Provides
    @IntoSet
    fun provideSpotifyProvider(provider: SpotifyProvider): MusicSourceProvider = provider
}
