package com.facet.music.core.location

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

interface LocationProvider {

    suspend fun lastKnownLocation(): GeoPoint?
}
