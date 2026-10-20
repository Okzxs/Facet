package com.my.music.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Геопозиция через FusedLocationProviderClient.
 * Не запрашивает разрешение само — работает только с уже выданным доступом.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val tag = "FusedLocation"
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): GeoPoint? {
        if (!hasLocationPermission()) {
            Log.d(tag, "Нет разрешения на геопозицию — лайк сохранится без координат")
            return null
        }

        // Последняя известная точка обычно отдаётся мгновенно; таймаут защищает лайк от зависания
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            try {
                client.lastLocation.awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Не удалось получить геопозицию", e)
                null
            }
        }

        return location?.let { GeoPoint(latitude = it.latitude, longitude = it.longitude) }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun Task<Location?>.awaitResult(): Location? =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            addOnCanceledListener { continuation.cancel() }
        }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 4_000L
    }
}
