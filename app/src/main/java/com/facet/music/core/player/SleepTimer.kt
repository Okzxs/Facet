package com.facet.music.core.player

val SleepTimerPresetsMinutes: List<Int> = listOf(15, 30, 60)

data class SleepTimerState(
    val minutes: Int,
    val endsAtMillis: Long,
)

fun sleepTimerEndTime(nowMillis: Long, minutes: Int): Long =
    nowMillis + minutes.coerceAtLeast(1).toLong() * 60_000L

fun sleepTimerRemainingMinutes(endsAtMillis: Long, nowMillis: Long): Int {
    val remaining = endsAtMillis - nowMillis
    if (remaining <= 0L) return 0
    return ((remaining + 59_999L) / 60_000L).toInt()
}
