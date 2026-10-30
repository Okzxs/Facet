package com.facet.music.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Виртуальное время корутин: `delay` внутри лимитера прокручивается мгновенно,
 * а часы лимитера берутся из планировщика теста, поэтому интервалы точные.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    /** Лимитер на виртуальных часах теста. */
    private fun TestScope.virtualLimiter(
        minIntervalMs: Long = RateLimiter.DEFAULT_MIN_INTERVAL_MS,
    ): RateLimiter = RateLimiter(minIntervalMs) { testScheduler.currentTime }

    @Test
    fun `два вызова подряд разнесены минимум на секунду`() = runTest {
        val limiter = virtualLimiter()

        limiter.awaitSlot()
        val first = currentTime
        limiter.awaitSlot()
        val second = currentTime

        assertEquals(1_000L, second - first)
    }

    @Test
    fun `три вызова занимают два интервала`() = runTest {
        val limiter = virtualLimiter()

        repeat(3) { limiter.awaitSlot() }

        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `параллельные вызовы встают в очередь без превышения лимита`() = runTest {
        val limiter = virtualLimiter()

        val times = listOf(1, 2, 3)
            .map { async { limiter.awaitSlot(); currentTime } }
            .awaitAll()
            .sorted()

        assertEquals(listOf(0L, 1_000L, 2_000L), times)
    }

    @Test
    fun `первый вызов проходит без ожидания и acquire возвращает результат`() = runTest {
        val limiter = virtualLimiter()

        val value = limiter.acquire { 42 }

        assertEquals(42, value)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `свой интервал соблюдается`() = runTest {
        val limiter = virtualLimiter(minIntervalMs = 250L)

        limiter.awaitSlot()
        limiter.awaitSlot()

        assertEquals(250L, currentTime)
    }
}
