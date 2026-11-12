package com.facet.music.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.facet.music.core.network.MediaClient
import com.facet.music.core.source.PlaybackResolver
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlaybackDataSourceFactory @Inject constructor(
    cache: PlaybackCache,
    resolver: PlaybackResolver,
    @MediaClient client: OkHttpClient,
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
