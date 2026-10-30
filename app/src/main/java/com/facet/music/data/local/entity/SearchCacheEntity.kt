package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сырой JSON-ответ поиска внешнего источника (Deezer, Apple, Spotify).
 *
 * Кэш живёт в Room, поэтому переживает перезапуск приложения: повторный
 * поиск того же запроса не ходит в сеть, пока запись моложе TTL.
 * Первичный ключ — текст запроса, поле [source] отделяет источники
 * друг от друга (при смене источника запись перезаписывается).
 */
@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @PrimaryKey val query: String,
    val source: String,
    val jsonResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
)
