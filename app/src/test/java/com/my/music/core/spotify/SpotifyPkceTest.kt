package com.my.music.core.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * PKCE и сборка URL авторизации Spotify.
 *
 * Вектор из RFC 7636 (приложение B): verifier "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
 * даёт challenge "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM". Если он ломается,
 * Spotify отклонит вход на обмене кода — поэтому проверяем именно эталон.
 */
class SpotifyPkceTest {

    @Test
    fun `challenge совпадает с вектором RFC 7636`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyPkce.challenge(verifier),
        )
    }

    @Test
    fun `verifier нужной длины и из разрешённого алфавита`() {
        val verifier = SpotifyPkce.generateVerifier(Random(42))
        assertEquals(SpotifyPkce.VERIFIER_LENGTH, verifier.length)
        assertTrue(
            "Недопустимые символы: $verifier",
            verifier.all { it.isLetterOrDigit() || it in "-._~" },
        )
    }

    @Test
    fun `state случаен и нужной длины`() {
        val first = SpotifyPkce.generateState(Random(1))
        val second = SpotifyPkce.generateState(Random(2))
        assertEquals(SpotifyPkce.STATE_LENGTH, first.length)
        assertNotEquals(first, second)
    }

    @Test
    fun `url авторизации содержит PKCE без client_secret`() {
        val url = spotifyAuthorizeUrl(
            clientId = "client-id",
            redirectUri = SPOTIFY_REDIRECT_URI,
            codeChallenge = "challenge",
            state = "state",
        )
        assertTrue(url.startsWith(SPOTIFY_AUTHORIZE_URL))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=challenge"))
        assertTrue(url.contains("state=state"))
        // Redirect URI с двоеточием и слэшами обязан быть закодирован.
        assertTrue(url.contains("redirect_uri=mymusic%3A%2F%2Fspotify-auth"))
        assertTrue(!url.contains("client_secret"))
    }

    @Test
    fun `url кодирует пробелы в списке прав`() {
        val url = spotifyAuthorizeUrl(
            clientId = "id",
            redirectUri = SPOTIFY_REDIRECT_URI,
            codeChallenge = "c",
            state = "s",
            scopes = "user-read-private playlist-read-private",
        )
        assertTrue(url.contains("scope=user-read-private+playlist-read-private"))
    }
}
