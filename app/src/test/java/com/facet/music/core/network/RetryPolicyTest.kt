package com.facet.music.core.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class RetryPolicyTest {

    @Test
    fun `429 и 503 повторяются дважды, прочие 5xx один раз`() {
        assertEquals(2, RetryPolicy.retryLimit(429))
        assertEquals(2, RetryPolicy.retryLimit(503))
        assertEquals(1, RetryPolicy.retryLimit(500))
        assertEquals(1, RetryPolicy.retryLimit(502))
        assertEquals(0, RetryPolicy.retryLimit(404))
    }

    @Test
    fun `Retry-After в секундах ограничен десятью секундами`() {
        assertEquals(3_000L, RetryPolicy.parseRetryAfter("3"))
        assertEquals(10_000L, RetryPolicy.parseRetryAfter("30"))
        assertEquals(0L, RetryPolicy.parseRetryAfter("-5"))
        assertEquals(null, RetryPolicy.parseRetryAfter(null))
    }

    @Test
    fun `Retry-After как HTTP-дата считается от текущего времени`() {
        val now = ZonedDateTime.parse(
            "Wed, 04 Nov 2026 20:00:00 GMT",
            DateTimeFormatter.RFC_1123_DATE_TIME,
        ).toInstant().toEpochMilli()

        assertEquals(5_000L, RetryPolicy.parseRetryAfter("Wed, 04 Nov 2026 20:00:05 GMT", now))
        assertEquals(10_000L, RetryPolicy.parseRetryAfter("Wed, 04 Nov 2026 20:10:00 GMT", now))
    }

    @Test
    fun `без Retry-After задержка растёт экспоненциально`() {
        val first = RetryPolicy.delayMs(429, null, attempt = 0, jitter = 10)
        val second = RetryPolicy.delayMs(429, null, attempt = 1, jitter = 10)
        assertEquals(510L, first)
        assertEquals(1_010L, second)
    }
}
