package com.facet.music.core.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

internal object RetryPolicy {

    const val MAX_RETRY_AFTER_MS = 10_000L

    fun retryLimit(code: Int): Int = when {
        code == 429 || code == 503 -> 2
        code in 500..599 -> 1
        else -> 0
    }

    fun delayMs(
        code: Int,
        retryAfter: String?,
        attempt: Int,
        nowMs: Long = System.currentTimeMillis(),
        jitter: Long = Random.nextLong(0L, 250L),
    ): Long = parseRetryAfter(retryAfter, nowMs)
        ?: (BASE_DELAY_MS shl attempt.coerceIn(0, 4)) + jitter

    fun parseRetryAfter(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val seconds = raw.toLongOrNull()
        if (seconds != null) return (seconds * 1000L).coerceIn(0L, MAX_RETRY_AFTER_MS)
        val dateMs = runCatching {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: return null
        return (dateMs - nowMs).coerceIn(0L, MAX_RETRY_AFTER_MS)
    }

    private const val BASE_DELAY_MS = 500L
}
