package com.facet.music.data.repository

import com.facet.music.data.model.Track

/**
 * Снимок очереди: треки и позиция текущего. Используется для восстановления
 * после перезапуска приложения.
 */
data class QueueSnapshot(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long = 0L,
)

/**
 * Сохранение очереди воспроизведения между запусками.
 */
interface QueueRepository {

    /** Прочитать сохранённую очередь; null — сохранённой очереди нет. */
    suspend fun load(): QueueSnapshot?

    /** Перезаписать сохранённую очередь целиком. */
    suspend fun save(tracks: List<Track>, currentIndex: Int, positionMs: Long = 0L)

    /** Очистить сохранённую очередь (например, когда она опустела). */
    suspend fun clear()
}
