package com.my.music.data.model

/**
 * Воспоминание о лайке: трек + место и сезон, когда он был сохранён.
 * Используется крупной Bento-карточкой на главном экране.
 */
data class SeasonMemory(
    val track: Track,
    val season: String?,
    val latitude: Double?,
    val longitude: Double?,
    val likedAt: Long,
)
