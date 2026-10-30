package com.facet.music.core.spotify

import java.net.URLEncoder

/**
 * Адрес, на который Spotify возвращает пользователя после входа.
 *
 * Должен быть ровно таким же в Spotify Developer Dashboard (поле Redirect URI),
 * иначе провайдер откажет ещё до экрана согласия.
 */
const val SPOTIFY_REDIRECT_URI: String = "facet://spotify-auth"

/** Страница согласия OAuth. */
const val SPOTIFY_AUTHORIZE_URL: String = "https://accounts.spotify.com/authorize"

/** Токен и обновление токена. */
const val SPOTIFY_TOKEN_URL: String = "https://accounts.spotify.com/api/token"

/** Профиль пользователя: имя и аватар для карточки интеграции. */
const val SPOTIFY_ME_URL: String = "https://api.spotify.com/v1/me"

/** Плейлисты пользователя (только чтение). */
const val SPOTIFY_MY_PLAYLISTS_URL: String = "https://api.spotify.com/v1/me/playlists"

/**
 * Права: профиль, свои плейлисты и любимые треки.
 * Стрим всё равно идёт через YouTube — звук Spotify приложению недоступен.
 */
const val SPOTIFY_SCOPES: String =
    "user-read-private user-read-email playlist-read-private playlist-read-collaborative"

/**
 * Состояние входа Spotify для UI.
 *
 * @param connected есть действующий refresh-токен;
 * @param displayName имя пользователя из `/v1/me` (null — профиль ещё не получен);
 * @param imageUrl аватар профиля.
 */
data class SpotifyAuthState(
    val connected: Boolean = false,
    val displayName: String? = null,
    val imageUrl: String? = null,
)

/** Плейлист пользователя Spotify — строка для импорта в медиатеку. */
data class SpotifyPlaylistSummary(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
)

/**
 * Собирает URL страницы согласия с PKCE.
 *
 * `response_type=code`, `code_challenge_method=S256`, без `client_secret`:
 * секрет в мобильном приложении не нужен и не должен попадать в APK.
 */
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
