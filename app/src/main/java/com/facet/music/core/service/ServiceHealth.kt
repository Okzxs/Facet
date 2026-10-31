package com.facet.music.core.service

/**
 * Состояние одного сервиса для UI.
 *
 * @param isAvailable последний известный результат проверки
 * @param isChecking true, пока идёт запрос к сервису
 * @param isConfigured настроен ли источник (у Spotify — введены ключи);
 * неактивный источник нельзя выбрать в поиске
 */
data class ServiceHealth(
    val isAvailable: Boolean = true,
    val isChecking: Boolean = false,
    val isConfigured: Boolean = true,
)
