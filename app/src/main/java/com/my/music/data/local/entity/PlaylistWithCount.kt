package com.my.music.data.local.entity

/**
 * Проекция «плейлист + число треков + обложка» для списков.
 *
 * Не таблица: Room собирает результат из запроса с `LEFT JOIN`, `COUNT`
 * и подзапросом за обложкой первого добавленного трека. Один запрос вместо
 * `N + 1` отдельных `COUNT` на каждую строку списка.
 */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    /** Обложка первого трека с картинкой; null — плейлист ещё пуст. */
    val coverUrl: String? = null,
)
