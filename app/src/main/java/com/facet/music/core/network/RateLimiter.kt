package com.facet.music.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Qualifier

/**
 * Ограничитель частоты запросов: не чаще одного запроса в [minIntervalMs].
 *
 * Слоты резервируются под мьютексом, ожидание — через [delay], поэтому
 * вызывающая корутина приостанавливается, а не блокирует поток
 * (запросы и так идут на `Dispatchers.IO`). Ожидание удерживает мьютекс:
 * так параллельные вызовы встают в очередь без гонки за слот.
 */
class RateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    /**
     * Монотонные часы в миллисекундах. Вынесены параметром ради тестов:
     * на виртуальном времени coroutines-test `System.nanoTime()` не двигается
     * вместе с `delay`, и слоты копились бы с лишним запасом.
     */
    private val clock: () -> Long = { System.nanoTime() / NANOS_IN_MILLIS },
) {

    private val mutex = Mutex()

    /** Момент (в мс по [clock]), раньше которого следующий запрос не пойдёт. */
    private var nextSlotAtMs: Long = Long.MIN_VALUE / 2

    /** Ждёт свободный слот. Первый вызов проходит сразу. */
    suspend fun awaitSlot() {
        mutex.withLock {
            val now = clock()
            val waitMs = nextSlotAtMs - now
            if (waitMs > 0) delay(waitMs)
            // Слот считается от момента запроса, а не от конца ожидания:
            // иначе при долгом ответе сети интервал «съедался» бы.
            nextSlotAtMs = maxOf(now, nextSlotAtMs) + minIntervalMs
        }
    }

    /** Ждёт слот и выполняет сетевой блок. */
    suspend fun <T> acquire(block: suspend () -> T): T {
        awaitSlot()
        return block()
    }

    companion object {
        /** Минимум одна секунда между запросами — предел публичных API источников. */
        const val DEFAULT_MIN_INTERVAL_MS: Long = 1_000L

        private const val NANOS_IN_MILLIS = 1_000_000L
    }
}

/**
 * Отдельный лимитер на источник: у Deezer и Spotify свои квоты,
 * поэтому запрос в один сервис не должен задерживать запрос в другой.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SourceRateLimiter(val value: String)

/** Имена источников для [SourceRateLimiter]. */
object RateLimiterKeys {
    const val DEEZER = "deezer"
    const val SPOTIFY = "spotify"
}
