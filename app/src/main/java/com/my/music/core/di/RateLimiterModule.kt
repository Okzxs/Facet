package com.my.music.core.di

import com.my.music.core.network.RateLimiter
import com.my.music.core.network.RateLimiterKeys
import com.my.music.core.network.SourceRateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Лимитеры частоты для внешних источников: по одному на сервис.
 *
 * Все три ограничены одной секундой между запросами — этого достаточно,
 * чтобы не ловить 429 от Deezer/iTunes и не превышать квоту Spotify.
 */
@Module
@InstallIn(SingletonComponent::class)
object RateLimiterModule {

    @Provides
    @Singleton
    @SourceRateLimiter(RateLimiterKeys.DEEZER)
    fun provideDeezerRateLimiter(): RateLimiter = RateLimiter(RateLimiter.DEFAULT_MIN_INTERVAL_MS)

    @Provides
    @Singleton
    @SourceRateLimiter(RateLimiterKeys.APPLE)
    fun provideAppleRateLimiter(): RateLimiter = RateLimiter(RateLimiter.DEFAULT_MIN_INTERVAL_MS)

    @Provides
    @Singleton
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY)
    fun provideSpotifyRateLimiter(): RateLimiter = RateLimiter(RateLimiter.DEFAULT_MIN_INTERVAL_MS)
}
