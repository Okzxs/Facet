package com.facet.music.data.repository

import com.facet.music.core.spotify.SpotifyAuthEvent
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.core.spotify.SpotifyPlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Вход в Spotify по OAuth 2.0 + PKCE и доступ к данным пользователя.
 *
 * Токены хранятся в шифрованном виде (Tink + Android Keystore): приложение
 * не видит пароль Spotify, только выданные провайдером токены.
 */
interface SpotifyAuthRepository {

    /** Текущее состояние входа — UI подписывается на поток. */
    val authState: StateFlow<SpotifyAuthState>

    /** Одноразовые события завершения входа для тостов. */
    val events: Flow<SpotifyAuthEvent>

    /** Похож ли адрес на возврат от Spotify (deep link `facet://spotify-auth`). */
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
