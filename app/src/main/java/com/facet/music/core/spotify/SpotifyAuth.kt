package com.facet.music.core.spotify

import java.net.URLEncoder

const val SPOTIFY_REDIRECT_URI: String = "facet://spotify-auth"

const val SPOTIFY_AUTHORIZE_URL: String = "https://accounts.spotify.com/authorize"

const val SPOTIFY_TOKEN_URL: String = "https://accounts.spotify.com/api/token"

const val SPOTIFY_ME_URL: String = "https://api.spotify.com/v1/me"

const val SPOTIFY_MY_PLAYLISTS_URL: String = "https://api.spotify.com/v1/me/playlists"

const val SPOTIFY_SCOPES: String =
    "user-read-private user-read-email user-library-read " +
        "playlist-read-private playlist-read-collaborative"

data class SpotifyAuthState(
    val connected: Boolean = false,
    val displayName: String? = null,
    val imageUrl: String? = null,
)

data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
)

fun spotifyAuthorizeUrl(
    clientId: String,
    redirectUri: String,
    codeChallenge: String,
    state: String,
    scopes: String = SPOTIFY_SCOPES,
): String {
    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    return buildString {
        append(SPOTIFY_AUTHORIZE_URL)
        append("?response_type=code")
        append("&client_id=").append(enc(clientId))
        append("&redirect_uri=").append(enc(redirectUri))
        append("&code_challenge_method=S256")
        append("&code_challenge=").append(enc(codeChallenge))
        append("&state=").append(enc(state))
        append("&scope=").append(enc(scopes))
    }
}
