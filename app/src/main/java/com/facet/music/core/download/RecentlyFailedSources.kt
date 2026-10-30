package com.facet.music.core.download

import com.facet.music.data.model.Source
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Короткая память о неудачных источниках для конкретного трека.
 *
 * Если YouTube не нашёл совпадение или не отдал progressive-поток,
 * повторный клик по «Скачать» не должен снова дёргать сеть каждую секунду.
 * Через [TTL_MS] попытка разрешается заново — например, после смены сети.
 */
@Singleton
class RecentlyFailedSources @Inject constructor() {

    private data class Key(
        val trackId: String,
        val source: Source,
    )

    private val failedAt = ConcurrentHashMap<Key, Long>()

    /** Часы вынесены в поле, чтобы тесты могли управлять TTL. */
    internal var clock: () -> Long = System::currentTimeMillis

    fun isFailed(trackId: String, source: Source): Boolean {
        val key = Key(trackId, source)
        val timestamp = failedAt[key] ?: return false
        if (clock() - timestamp >= TTL_MS) {
            failedAt.remove(key)
            return false
        }
        return true
    }

    fun markFailed(trackId: String, source: Source) {
        failedAt[Key(trackId, source)] = clock()
    }

    fun clear(trackId: String, source: Source) {
        failedAt.remove(Key(trackId, source))
    }

    companion object {
        /** Сколько минут не повторять запрос к упавшему источнику. */
        const val TTL_MS = 30 * 60 * 1000L
    }
}
