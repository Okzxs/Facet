package com.facet.music.core.service

import android.util.Log
import com.facet.music.core.network.ApiClient
import com.facet.music.core.network.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceProbe @Inject constructor(
    @ApiClient private val client: OkHttpClient,
) {

    suspend fun isReachable(type: ServiceType): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        try {
            val code = client.executeCancellable(
                Request.Builder().url(urlFor(type)).head().build(),
            ) { response -> response.code }
            code < 500
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "${type.name}: ${e.javaClass.simpleName}")
            false
        }
    } ?: false

    private fun urlFor(type: ServiceType): String = when (type) {
        ServiceType.SOUNDCLOUD -> "https://soundcloud.com/"
        ServiceType.YOUTUBE -> "https://www.youtube.com/"
        ServiceType.SPOTIFY -> "https://api.spotify.com/v1/"
    }

    private companion object {
        const val TAG = "ServiceProbe"
        const val TIMEOUT_MS = 5_000L
    }
}
