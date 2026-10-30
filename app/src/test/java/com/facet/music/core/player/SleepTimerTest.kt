package com.facet.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Чистая логика таймера сна: пресеты, время окончания и остаток минут. */
class SleepTimerTest {

    private val minute = 60_000L

    @Test
    fun `presets are 15 30 and 60 minutes`() {
        assertEquals(listOf(15, 30, 60), SleepTimerPresetsMinutes)
    }

    @Test
    fun `end time adds selected minutes`() {
        assertEquals(15 * minute, sleepTimerEndTime(nowMillis = 0L, minutes = 15))
        assertEquals(60 * minute, sleepTimerEndTime(nowMillis = 0L, minutes = 60))
    }

    @Test
    fun `end time keeps at least one minute for non positive input`() {
        assertEquals(minute, sleepTimerEndTime(nowMillis = 0L, minutes = 0))
        assertEquals(minute, sleepTimerEndTime(nowMillis = 0L, minutes = -30))
    }

    @Test
    fun `remaining minutes round up while timer is running`() {
        val endsAt = 30 * minute

        assertEquals(30, sleepTimerRemainingMinutes(endsAt, nowMillis = 0L))
        // За 20 секунд до конца показываем «1 мин», а не «0»: таймер ещё идёт.
        assertEquals(1, sleepTimerRemainingMinutes(endsAt, endsAt - 20_000L))
        assertEquals(2, sleepTimerRemainingMinutes(endsAt, endsAt - 90_000L))
    }

    @Test
    fun `remaining minutes are zero after the timer runs out`() {
        val endsAt = 15 * minute

        assertEquals(0, sleepTimerRemainingMinutes(endsAt, endsAt))
        assertEquals(0, sleepTimerRemainingMinutes(endsAt, endsAt + minute))
    }
}
