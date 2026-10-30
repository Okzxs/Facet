package com.my.music.data.source.external

import android.content.Context
import android.net.Uri
import android.util.Log
import com.my.music.core.network.RateLimiter
import com.my.music.core.network.RateLimiterKeys
import com.my.music.core.network.SourceRateLimiter
import com.my.music.core.spotify.SPOTIFY_AUTHORIZE_URL
import com.my.music.core.spotify.SPOTIFY_ME_URL
import com.my.music.core.spotify.SPOTIFY_MY_PLAYLISTS_URL
import com.my.music.core.spotify.SPOTIFY_REDIRECT_URI
import com.my.music.core.spotify.SPOTIFY_TOKEN_URL
import com.my.music.core.spotify.SpotifyAuthState
import com.my.music.core.spotify.SpotifyPkce
import com.my.music.core.spotify.SpotifyPlaylistSummary
import com.my.music.core.spotify.spotifyAuthorizeUrl
import com.my.music.data.repository.SpotifyAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Вход в Spotify по OAuth 2.0 + PKCE.
 *
 * Почему PKCE, а не Client Credentials: пользовательский вход даёт доступ
 * к его плейлистам, а `client_secret` в мобильном приложении хранить нельзя —
 * его можно вытащить из APK. Секрет остаётся опциональным и нужен только
 * для поиска без входа.
 *
 * Токены лежат в SharedPreferences `spotify_auth` рядом с остальными
 * настройками: пароль Spotify приложение не видит и не хранит.
 */
@Singleton
class SpotifyAuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY) private val rateLimiter: RateLimiter,
) : SpotifyAuthRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Обновление токена одно на всё приложение: параллельные запросы
    // не должны одновременно дёргать refresh и затирать результат.
    private val tokenMutex = Mutex()

    private val _authState = MutableStateFlow(readState())
    override val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    override fun isRedirect(uri: String): Boolean =
        uri.startsWith(SPOTIFY_REDIRECT_URI, ignoreCase = true)

    override suspend fun startAuthorization(clientId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = clientId.trim()
                require(id.isNotEmpty()) { MISSING_CLIENT_ID }
                val verifier = SpotifyPkce.generateVerifier()
                val state = SpotifyPkce.generateState()
                prefs.edit()
                    .putString(KEY_VERIFIER, verifier)
                    .putString(KEY_STATE, state)
                    .putString(KEY_CLIENT_ID, id)
                    .apply()
                spotifyAuthorizeUrl(
                    clientId = id,
                    redirectUri = SPOTIFY_REDIRECT_URI,
                    codeChallenge = SpotifyPkce.challenge(verifier),
                    state = state,
                )
            }.onFailure { error ->
                Log.w(TAG, "Не удалось начать вход Spotify: ${error.message}")
            }
        }

    override suspend fun completeAuthorization(uri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                parsed.getQueryParameter("error")?.let { error ->
                    error("Spotify вернул ошибку авторизации: $error")
                }
                val code = parsed.getQueryParameter("code")
                    ?: error("В ответе Spotify нет кода авторизации")
                val returnedState = parsed.getQueryParameter("state")
                    ?: error("В ответе Spotify нет state")
                val expectedState = prefs.getString(KEY_STATE, null)
                if (expectedState == null || expectedState != returnedState) {
                    error("state не совпал: ответ пришёл не на наш запрос")
                }
                val verifier = prefs.getString(KEY_VERIFIER, null)
                    ?: error("Потерян code_verifier — начните вход заново")
                val clientId = prefs.getString(KEY_CLIENT_ID, null)
                    ?: error(MISSING_CLIENT_ID)

                rateLimiter.awaitSlot()
                val form = FormBody.Builder()
                    .add("grant_type", GRANT_TYPE_CODE)
                    .add("code", code)
                    .add("redirect_uri", SPOTIFY_REDIRECT_URI)
                    .add("client_id", clientId)
                    .add("code_verifier", verifier)
                    .build()
                val tokenBody = postForm(SPOTIFY_TOKEN_URL, form)
                saveTokens(tokenBody)
                // Профиль нужен карточке настроек: имя и аватар.
                runCatching { loadProfile() }.onFailure { error ->
                    Log.w(TAG, "Профиль Spotify не загрузился: ${error.message}")
                }
                prefs.edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
                _authState.value = readState()
            }.onFailure { error ->
                Log.w(TAG, "Вход Spotify не завершён: ${error.message}")
            }
        }

    override suspend fun accessToken(forceRefresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            val refreshToken = prefs.getString(KEY_REFRESH, null) ?: return@withContext null
            val cached = prefs.getString(KEY_ACCESS, null)
            if (!forceRefresh && cached != null && isFresh()) return@withContext cached

            tokenMutex.withLock {
                val current = prefs.getString(KEY_ACCESS, null)
                if (!forceRefresh && current != null && isFresh()) return@withLock current
                val clientId = prefs.getString(KEY_CLIENT_ID, null)
                if (clientId.isNullOrBlank()) return@withLock null
                runCatching { refresh(clientId, refreshToken) }
                    .onFailure { error ->
                        Log.w(TAG, "Токен Spotify не обновился: ${error.message}")
                    }
                    .getOrNull()
            }
        }

    override suspend fun fetchMyPlaylists(): Result<List<SpotifyPlaylistSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken() ?: error("Нужен вход в Spotify")
                val body = get("$SPOTIFY_MY_PLAYLISTS_URL?limit=$PLAYLISTS_LIMIT", token)
                parsePlaylists(body)
            }.onFailure { error ->
                Log.w(TAG, "Плейлисты Spotify не загрузились: ${error.message}")
            }
        }

    override suspend fun signOut() {
        prefs.edit().clear().apply()
        _authState.value = SpotifyAuthState()
    }

    /** Токен ещё жив с запасом в минуту — не тратим запрос на обновление. */
    private fun isFresh(): Boolean =
        System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES_AT, 0L) - TOKEN_MARGIN_MS

    private suspend fun refresh(clientId: String, refreshToken: String): String {
        rateLimiter.awaitSlot()
        val form = FormBody.Builder()
            .add("grant_type", GRANT_TYPE_REFRESH)
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()
        val body = postForm(SPOTIFY_TOKEN_URL, form)
        saveTokens(body)
        _authState.value = readState()
        return prefs.getString(KEY_ACCESS, null).orEmpty()
    }

    /** Разбор ответа token endpoint: access, refresh и срок жизни. */
    private fun saveTokens(body: String) {
        val root = json.parseToJsonElement(body).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: error("Spotify не вернул access_token")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val expiresIn = root["expires_in"]?.jsonPrimitive?.intOrNull ?: DEFAULT_TTL_SECONDS
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)
            .apply {
                // При обновлении Spotify может не прислать refresh_token —
                // тогда остаётся прежний.
                if (refresh != null) putString(KEY_REFRESH, refresh)
            }
            .apply()
    }

    private fun loadProfile() {
        val access = prefs.getString(KEY_ACCESS, null) ?: return
        val root = json.parseToJsonElement(get(SPOTIFY_ME_URL, access)).jsonObject
        val name = root["display_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: root["id"]?.jsonPrimitive?.contentOrNull
        val image = root["images"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
        prefs.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_IMAGE, image)
            .apply()
    }

    private fun parsePlaylists(body: String): List<SpotifyPlaylistSummary> =
        json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
            ?.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val cover = item["images"]?.jsonArray
                    ?.mapNotNull { image ->
                        val obj = image.jsonObject
                        val url = obj["url"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                        url to (obj["width"]?.jsonPrimitive?.intOrNull ?: 0)
                    }
                    ?.maxByOrNull { it.second }
                    ?.first
                SpotifyPlaylistSummary(
                    id = id,
                    name = name,
                    coverUrl = cover,
                    trackCount = item["tracks"]?.jsonObject
                        ?.get("total")?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
            .orEmpty()

    private fun readState(): SpotifyAuthState {
        val connected = !prefs.getString(KEY_REFRESH, null).isNullOrBlank() ||
            !prefs.getString(KEY_ACCESS, null).isNullOrBlank()
        return SpotifyAuthState(
            connected = connected,
            displayName = prefs.getString(KEY_NAME, null),
            imageUrl = prefs.getString(KEY_IMAGE, null),
        )
    }

    private fun postForm(url: String, form: FormBody): String {
        val request = Request.Builder().url(url).post(form).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Spotify token HTTP ${response.code}: $body")
            return body
        }
    }

    private fun get(url: String, token: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Spotify HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val TAG = "SpotifyAuth"
        const val PREFS_NAME = "spotify_auth"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_VERIFIER = "code_verifier"
        const val KEY_STATE = "oauth_state"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_NAME = "display_name"
        const val KEY_IMAGE = "avatar_url"
        const val GRANT_TYPE_CODE = "authorization_code"
        const val GRANT_TYPE_REFRESH = "refresh_token"
        const val DEFAULT_TTL_SECONDS = 3600
        const val TOKEN_MARGIN_MS = 60_000L
        const val PLAYLISTS_LIMIT = 50
        const val MISSING_CLIENT_ID = "Не задан Client ID Spotify"
    }
}
