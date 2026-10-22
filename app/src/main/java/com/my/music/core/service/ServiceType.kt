package com.my.music.core.service

/**
 * Сервисы, доступность которых отслеживает приложение.
 * Это не то же самое, что [com.my.music.data.model.Source]:
 * здесь только источники, для которых есть онлайн-проверка.
 */
enum class ServiceType(val displayName: String) {
    SOUNDCLOUD("SoundCloud"),
    YOUTUBE("YouTube"),
}
