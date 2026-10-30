package com.my.music.data.repository

import com.my.music.core.spotify.SpotifyAuthState
import com.my.music.core.spotify.SpotifyPlaylistSummary
import kotlinx.coroutines.flow.StateFlow

/**
 * Вход в Spotify по OAuth 2.0 + PKCE и доступ к данным пользователя.
 *
 * Токены живут в SharedPreferences: приложение не хранит пароль Spotify,
 * только выданные провайдером токены, которые можно отозвать.
 */
interface SpotifyAuthRepository {

    /** Текущее состояние входа — UI подписывается на поток. */
    val authState: StateFlow<SpotifyAuthState>

    /** Похож ли адрес на возврат от Spotify (deep link `mymusic://spotify-auth`). */
    fun isRedirect(uri: String): Boolean

    /**
     * Начинает вход: сохраняет verifier/state и возвращает URL, который нужно
     * открыть в браузере. Без Client ID вернёт ошибку.
     */
    suspend fun startAuthorization(clientId: String): Result<String>

    /**
     * Завершает вход по deep link: проверяет state, меняет код на токены
     * и загружает профиль. Повторный вызов с тем же кодом бесполезен.
     */
    suspend fun completeAuthorization(uri: String): Result<Unit>

    /**
     * Действующий access-токен (при истечении обновляется refresh-токеном).
     * null — пользователь не вошёл или обновить токен не удалось.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String?

    /** Плейлисты пользователя для импорта в медиатеку. */
    suspend fun fetchMyPlaylists(): Result<List<SpotifyPlaylistSummary>>

    /** Выход: токены и профиль стираются, поиск остаётся на Client Credentials. */
    suspend fun signOut()
}
