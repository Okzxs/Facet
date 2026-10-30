package com.my.music.data.local.entity

/**
 * Проекция «плейлист + число треков» для списков.
 *
 * Не таблица: Room собирает результат из запроса с `LEFT JOIN` и `COUNT`.
 * Один запрос вместо `N + 1` отдельных `COUNT` на каждую строку списка.
 */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)
