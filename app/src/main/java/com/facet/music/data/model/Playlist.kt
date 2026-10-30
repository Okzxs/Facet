package com.facet.music.data.model

/**
 * Плейлист для UI.
 *
 * @param trackCount число треков, посчитанное одним джойном в запросе списка —
 *   отдельный COUNT на каждую строку не нужен;
 * @param coverUrl обложка первого трека с картинкой (null у пустого плейлиста);
 *   UI подставляет тональный градиент, если картинки нет.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val coverUrl: String? = null,
)
