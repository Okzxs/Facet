package com.facet.music.core.spotify

sealed interface SpotifyAuthEvent {
    data object SignedIn : SpotifyAuthEvent

    data object Failed : SpotifyAuthEvent
}
