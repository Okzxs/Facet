package com.facet.music.core.spotify

/** Событие завершения входа Spotify для показа тоста в UI. */
sealed interface SpotifyAuthEvent {
    data object SignedIn : SpotifyAuthEvent

    data object Failed : SpotifyAuthEvent
}
