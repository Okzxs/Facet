package com.facet.music.core.player

/** Длительности таймера сна, которые предлагает плеер (минуты). */
val SleepTimerPresetsMinutes: List<Int> = listOf(15, 30, 60)

/**
 * Активный таймер сна: выбранная длительность и момент окончания.
 *
 * Храним время окончания, а не оставшиеся минуты: строка «Остановится в 23:45»
 * не зависит от того, когда её отрисовали.
 */
data class SleepTimerState(
    val minutes: Int,
    val endsAtMillis: Long,
)

/** Время окончания таймера в миллисекундах эпохи: [nowMillis] + [minutes] минут. */
fun sleepTimerEndTime(nowMillis: Long, minutes: Int): Long =
    nowMillis + minutes.coerceAtLeast(1).toLong() * 60_000L

/**
 * Сколько полных/начатых минут осталось до [endsAtMillis].
 *
 * Округление вверх: за 20 секунд до конца пользователь видит «1 мин»,
 * а не «0 мин» — ноль означал бы уже сработавший таймер.
 */
fun sleepTimerRemainingMinutes(endsAtMillis: Long, nowMillis: Long): Int {
    val remaining = endsAtMillis - nowMillis
    if (remaining <= 0L) return 0
    return ((remaining + 59_999L) / 60_000L).toInt()
}
