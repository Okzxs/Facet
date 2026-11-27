package com.facet.music.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlaybackPrefetcher @Inject constructor(
    private val cache: PlaybackCache,
    private val dataSourceFactory: PlaybackDataSourceFactory,
    private val resolver: PlaybackResolver,
) {

    suspend fun prefetch(track: Track) = withContext(Dispatchers.IO) {
        if (track.id.isBlank() || cache.hasData(track.id)) return@withContext
        val stream = resolver.resolve(track).getOrNull() ?: return@withContext
        if (stream.isHls) return@withContext
        resolver.remember(track)
        val spec = DataSpec.Builder()
            .setUri(PlaybackUri.forTrack(track.id))
            .setLength(PREFETCH_BYTES)
            .build()
        val source = dataSourceFactory.cacheDataSourceFactory.createDataSourceForDownloading()
        try {
            runCatching {
                CacheWriter(source, spec, ByteArray(BUFFER_BYTES), null).cache()
            }
        } finally {
            runCatching { source.close() }
        }
    }

    private companion object {
        const val PREFETCH_BYTES = 2L * 1024L * 1024L
        const val BUFFER_BYTES = 64 * 1024
    }
}
