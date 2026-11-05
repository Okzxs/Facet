package com.facet.music.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственный на процесс кэш Media3. Файлы переживают протухание подписанных
 * ссылок: ключ элемента — id трека, а не URL.
 */
@UnstableApi
@Singleton
class PlaybackCache @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val databaseProvider = StandaloneDatabaseProvider(context)

    val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, DIRECTORY),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            databaseProvider,
        )
    }

    fun sizeBytes(): Long = cache.cacheSpace

    /** Открыть SimpleCache заранее, чтобы первый кадр не читал базу на main. */
    fun warmUp() {
        cache
    }

    fun keyCount(): Int = cache.keys.size

    /** Есть ли в кэше хоть один байт трека — нужен для офлайн-фолбэка. */
    fun hasData(trackId: String): Boolean =
        cache.getCachedLength(trackId, 0L, C.LENGTH_UNSET.toLong()) > 0L

    fun clear() {
        cache.keys.toList().forEach(cache::removeResource)
    }

    /** Удалить закэшированный поток трека: MIME мог смениться (HLS ↔ progressive). */
    fun remove(trackId: String) {
        runCatching { cache.removeResource(trackId) }
    }

    private companion object {
        const val DIRECTORY = "media3_cache_v2"
        const val MAX_BYTES = 1024L * 1024 * 1024
    }
}
