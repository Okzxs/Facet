package com.my.music.data.model

/**
 * Плейлист для UI.
 *
 * @param trackCount число треков, посчитанное одним джойном в запросе списка —
 *   отдельный COUNT на каждую строку не нужен.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
)
