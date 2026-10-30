package com.facet.music.core.service

import com.facet.music.data.model.Source

/**
 * Сервисы, доступность которых отслеживает приложение и между которыми
 * переключается поиск. Это не то же самое, что [Source]: здесь только те
 * источники, у которых есть онлайн-поиск.
 *
 * Порядок объявления — порядок в меню статусов. Ряд кнопок-источников
 * в поиске задаётся отдельно ([com.facet.music.feature.search] — порядок брендов).
 */
enum class ServiceType(val displayName: String, val source: Source) {
    SOUNDCLOUD("SoundCloud", Source.SOUNDCLOUD),
    YOUTUBE("YouTube", Source.YOUTUBE),
    DEEZER("Deezer", Source.DEEZER),
    APPLE("Apple Music", Source.APPLE),
    SPOTIFY("Spotify", Source.SPOTIFY),
}
