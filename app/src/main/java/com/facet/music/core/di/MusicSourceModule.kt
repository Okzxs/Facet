package com.facet.music.core.di

import com.facet.music.core.source.MusicSourceProvider
import com.facet.music.data.source.external.AppleMusicProvider
import com.facet.music.data.source.external.DeezerProvider
import com.facet.music.data.source.external.SpotifyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Внешние источники метаданных (Deezer, Apple/iTunes, Spotify).
 *
 * Новый провайдер добавляется одним `@IntoSet`-биндингом: поиск, статусы
 * и резолвер стрима подхватят его автоматически через
 * [com.facet.music.core.source.MusicSourceRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
object MusicSourceModule {

    @Provides
    @IntoSet
    fun provideDeezerProvider(provider: DeezerProvider): MusicSourceProvider = provider

    @Provides
    @IntoSet
    fun provideAppleMusicProvider(provider: AppleMusicProvider): MusicSourceProvider = provider

    @Provides
    @IntoSet
    fun provideSpotifyProvider(provider: SpotifyProvider): MusicSourceProvider = provider
}
