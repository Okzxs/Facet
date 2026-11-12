package com.facet.music.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Qualifier

class RateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_IN_MILLIS },
) {

    private val mutex = Mutex()

    private var nextSlotAtMs: Long = Long.MIN_VALUE / 2

    suspend fun awaitSlot() {
        mutex.withLock {
            val now = clock()
            val waitMs = nextSlotAtMs - now
            if (waitMs > 0) delay(waitMs)
            nextSlotAtMs = maxOf(now, nextSlotAtMs) + minIntervalMs
        }
    }

    suspend fun <T> acquire(block: suspend () -> T): T {
        awaitSlot()
        return block()
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS: Long = 1_000L

        private const val NANOS_IN_MILLIS = 1_000_000L
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourceRateLimiter(val value: String)

object RateLimiterKeys {
    const val DEEZER = "deezer"
    const val SPOTIFY = "spotify"
}
