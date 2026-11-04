package com.facet.music.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.facet.music.core.network.NetworkModule
import com.facet.music.core.source.PlaybackResolver
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Цепочка Media3: кэш → ResolvingDataSource → OkHttp. Один кэш на процесс,
 * один upstream на все элементы, HLS и progressive идут через одну цепочку.
 */
@UnstableApi
@Singleton
class PlaybackDataSourceFactory @Inject constructor(
    cache: PlaybackCache,
    resolver: PlaybackResolver,
    @Named(NetworkModule.MEDIA_CLIENT) client: OkHttpClient,
) {

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache.cache)
        .setUpstreamDataSourceFactory(
            ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(client),
                PlaybackDataSpecResolver(resolver),
            ),
        )
        .setCacheKeyFactory(PlaybackCacheKeyFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
