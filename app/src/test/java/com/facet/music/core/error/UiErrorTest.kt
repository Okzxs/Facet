package com.facet.music.core.error

import com.facet.music.core.player.toFriendlyPlaybackError
import com.facet.music.core.source.ResolveError
import com.facet.music.core.source.ResolveException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiErrorTest {

    @Test
    fun `resolve exception maps to typed ui error`() {
        assertEquals(
            UiError.TrackUnavailable,
            ResolveException(ResolveError.NotAvailable).toFriendlyPlaybackError(),
        )
        assertEquals(
            UiError.SubscriptionOnly,
            ResolveException(ResolveError.SubscriptionOnly).toFriendlyPlaybackError(),
        )
        assertEquals(
            UiError.GeoBlocked,
            ResolveException(ResolveError.GeoBlocked).toFriendlyPlaybackError(),
        )
        assertEquals(
            UiError.NoMatch,
            ResolveException(ResolveError.NoMatch).toFriendlyPlaybackError(),
        )
    }

    @Test
    fun `network and unknown resolve errors use fallback`() {
        assertEquals(
            UiError.Lyrics,
            ResolveException(ResolveError.Network).toFriendlyPlaybackError(UiError.Lyrics),
        )
        assertEquals(
            UiError.Playback,
            ResolveException(ResolveError.Unknown).toFriendlyPlaybackError(),
        )
    }

    @Test
    fun `java exceptions classify to network kinds`() {
        assertEquals(
            UiError.NoInternet,
            UnknownHostException("unable to resolve host").toFriendlyPlaybackError(),
        )
        assertEquals(
            UiError.Timeout,
            SocketTimeoutException("timeout").toFriendlyPlaybackError(),
        )
        assertEquals(
            UiError.Playback,
            RuntimeException("mysterious").toFriendlyPlaybackError(),
        )
    }

    @Test
    fun `caused by resolve exception keeps typed mapping`() {
        val error = RuntimeException("player failed", ResolveException(ResolveError.GeoBlocked))
        assertEquals(UiError.GeoBlocked, error.toFriendlyPlaybackError())
    }

    @Test
    fun `every ui error has unique resource`() {
        val resources = listOf(
            UiError.NoInternet,
            UiError.Timeout,
            UiError.SecureConnection,
            UiError.Playback,
            UiError.Lyrics,
            UiError.TrackUnavailable,
            UiError.SubscriptionOnly,
            UiError.GeoBlocked,
            UiError.NoMatch,
            UiError.PlayerUnavailable,
            UiError.LikeFailed,
            UiError.DislikeFailed,
        ).map { it.messageRes }
        assertEquals(resources.size, resources.distinct().size)
        assertTrue(resources.all { it != 0 })
    }
}
