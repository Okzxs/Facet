package com.my.music.core.location

/**
 * Точка геопозиции, сохранённая вместе с лайком.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Источник последней известной геопозиции.
 */
interface LocationProvider {

    /**
     * Последняя известная геопозиция или null, если нет разрешения,
     * провайдер выключен либо данные недоступны.
     */
    suspend fun lastKnownLocation(): GeoPoint?
}
