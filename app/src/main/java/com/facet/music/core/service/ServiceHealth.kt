package com.facet.music.core.service

/**
 * Состояние одного сервиса для UI.
 *
 * @param isAvailable последний известный результат проверки
 * @param isChecking true, пока идёт запрос к сервису
 */
data class ServiceHealth(
    val isAvailable: Boolean = true,
    val isChecking: Boolean = false,
)
