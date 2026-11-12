package com.facet.music.core.di

import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RateLimiterModule {

    @Provides
    @Singleton
    @SourceRateLimiter(RateLimiterKeys.DEEZER)
    fun provideDeezerRateLimiter(): RateLimiter = RateLimiter(RateLimiter.DEFAULT_MIN_INTERVAL_MS)

    @Provides
    @Singleton
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY)
    fun provideSpotifyRateLimiter(): RateLimiter = RateLimiter(RateLimiter.DEFAULT_MIN_INTERVAL_MS)
}
