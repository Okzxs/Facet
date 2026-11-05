package com.facet.music.core.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.facet.music.core.source.PlaybackResolver
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * При 401/403/404/410 ссылка считается протухшей: сбрасываем in-memory поток
 * и закэшированный ответ, ResolvingDataSource резолвит заново, позиция
 * воспроизведения сохраняется. Не больше двух таких повторов на трек.
 */
@UnstableApi
@Singleton
class PlaybackLoadErrorPolicy @Inject constructor(
    private val resolver: PlaybackResolver,
    private val cache: PlaybackCache,
) : DefaultLoadErrorHandlingPolicy() {

    private val invalidations = ConcurrentHashMap<String, Int>()

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)
            ?.responseCode
            ?: return super.getRetryDelayMsFor(loadErrorInfo)
        if (code !in EXPIRED_CODES) return super.getRetryDelayMsFor(loadErrorInfo)

        val dataSpec = loadErrorInfo.loadEventInfo.dataSpec
        val trackId = PlaybackUri.trackId(dataSpec.uri)
            ?: dataSpec.key?.takeIf { resolver.isKnownTrack(it) }
            ?: resolver.activeTrackId.value
            ?: return super.getRetryDelayMsFor(loadErrorInfo)

        val attempts = invalidations[trackId] ?: 0
        if (attempts >= MAX_INVALIDATIONS) return C.TIME_UNSET
        invalidations[trackId] = attempts + 1
        cache.remove(trackId)
        resolver.invalidate(trackId)
        return RETRY_DELAY_MS
    }

    private companion object {
        val EXPIRED_CODES = setOf(401, 403, 404, 410)
        const val MAX_INVALIDATIONS = 2
        const val RETRY_DELAY_MS = 250L
    }
}

/** Ключ кэша: id трека для элемента плейлиста, стабильный путь для HLS-сегментов. */
@UnstableApi
object PlaybackCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String =
        PlaybackUri.trackId(dataSpec.uri)
            ?: dataSpec.uri.toString().substringBefore('?')
}
