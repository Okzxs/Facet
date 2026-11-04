package com.facet.music.core.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.facet.music.core.source.PlaybackResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Мост между ExoPlayer и единым резолвером. Вызывается на потоке загрузчика,
 * поэтому здесь и только здесь допустим runBlocking с общим дедлайном.
 */
@UnstableApi
@Singleton
class PlaybackDataSpecResolver @Inject constructor(
    private val resolver: PlaybackResolver,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val trackId = PlaybackUri.trackId(dataSpec.uri) ?: return dataSpec

        val stream = try {
            runBlocking {
                withTimeout(RESOLVE_TIMEOUT_MS) {
                    resolver.resolveById(trackId)
                }
            }.getOrElse { error ->
                throw IOException("Резолв потока не удался", error)
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Резолв потока не удался", e)
        }

        val uri = runCatching { Uri.parse(stream.url) }.getOrElse {
            throw IOException("Резолвер вернул некорректный URL", it)
        }
        return dataSpec
            .withUri(uri)
            .withRequestHeaders(stream.headers)
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 20_000L
    }
}
