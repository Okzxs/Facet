package com.facet.music.core.player

import androidx.media3.common.PlaybackException
import com.facet.music.core.error.UiError
import com.facet.music.core.source.ResolveError
import com.facet.music.core.source.ResolveException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Throwable.toFriendlyPlaybackError(fallback: UiError = UiError.Playback): UiError {
    (this as? ResolveException)?.let { error ->
        return error.error.toUiError(fallback)
    }
    val chain = generateSequence(this) { it.cause }.take(8).toList()
    chain.filterIsInstance<ResolveException>().firstOrNull()?.let { error ->
        return error.error.toUiError(fallback)
    }
    val messages = chain.mapNotNull { it.message?.lowercase() }
    val noInternet = chain.any { it is UnknownHostException || it is ConnectException } ||
        messages.any {
            "unable to resolve host" in it ||
                "no address associated with hostname" in it ||
                "network is unreachable" in it ||
                "failed to connect" in it
        }
    if (noInternet) return UiError.NoInternet

    val timeout = chain.any { it is SocketTimeoutException } ||
        messages.any { "timeout" in it || "timed out" in it }
    if (timeout) return UiError.Timeout

    if (chain.any { it is SSLException }) return UiError.SecureConnection

    if (chain.any { it is SocketException }) return UiError.NoInternet

    return fallback
}

fun PlaybackException.toFriendlyPlaybackError(): UiError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> UiError.Timeout
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> UiError.NoInternet
    else -> cause?.toFriendlyPlaybackError() ?: UiError.Playback
}

private fun ResolveError.toUiError(fallback: UiError): UiError = when (this) {
    ResolveError.NotAvailable -> UiError.TrackUnavailable
    ResolveError.SubscriptionOnly -> UiError.SubscriptionOnly
    ResolveError.GeoBlocked -> UiError.GeoBlocked
    ResolveError.NoMatch -> UiError.NoMatch
    ResolveError.Network, ResolveError.Unknown -> fallback
}
