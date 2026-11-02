package com.facet.music.data.source.external

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import com.facet.music.core.security.SecureStore
import com.facet.music.core.spotify.SPOTIFY_AUTHORIZE_URL
import com.facet.music.core.spotify.SPOTIFY_ME_URL
import com.facet.music.core.spotify.SPOTIFY_MY_PLAYLISTS_URL
import com.facet.music.core.spotify.SPOTIFY_REDIRECT_URI
import com.facet.music.core.spotify.SPOTIFY_TOKEN_URL
import com.facet.music.core.spotify.SpotifyAuthEvent
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.core.spotify.SpotifyPkce
import com.facet.music.core.spotify.SpotifyPlaylistSummary
import com.facet.music.core.spotify.spotifyAuthorizeUrl
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.repository.SpotifyAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
 * Код на токены меняется в области процесса, а не Activity: пересоздание
 * экрана (язык, тема, поворот) не должно запускать обмен повторно.
 */
@Singleton
class SpotifyAuthRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    @SourceRateLimiter(RateLimiterKeys.SPOTIFY) private val rateLimiter: RateLimiter,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : SpotifyAuthRepository {

    private val appContext = context.applicationContext

    // Старый открытый файл: нужен только как источник одноразовой миграции.
    private val legacyPrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secureStore by lazy { SecureStore(appContext) }

    // Обновление токена одно на всё приложение: параллельные запросы
    // не должны одновременно дёргать refresh и затирать результат.
    private val tokenMutex = Mutex()

    // Обмен кода сериализован: повторный deep link не должен отправлять
    // тот же одноразовый код второй раз.
    private val completionMutex = Mutex()
    private var lastCompletedCode: String? = null

    private val eventChannel = Channel<SpotifyAuthEvent>(Channel.BUFFERED)
    override val events: Flow<SpotifyAuthEvent> = eventChannel.receiveAsFlow()

    private val _authState = MutableStateFlow(
        SpotifyAuthState(connected = hasStoredSession()),
    )
    override val authState: StateFlow<SpotifyAuthState> = _authState.asStateFlow()

    // Пока миграция не завершена, suspend-методы не читают шифрованные ключи:
    // иначе старые токены на миг выглядели бы отсутствующими.
    private val migrated = CompletableDeferred<Unit>()

    init {
        applicationScope.launch {
            runCatching { secureStore.migrateLegacy(legacyPrefs, LEGACY_KEYS) }
                .onFailure { error ->
                    Log.w(TAG, "Миграция секретов Spotify не удалась: ${error.message}")
                }
            _authState.value = readState()
            migrated.complete(Unit)
        }
    }

    override fun isRedirect(uri: String): Boolean =
        uri.startsWith(SPOTIFY_REDIRECT_URI, ignoreCase = true)

    override suspend fun startAuthorization(clientId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = clientId.trim()
                require(id.isNotEmpty()) { MISSING_CLIENT_ID }
                val verifier = SpotifyPkce.generateVerifier()
                val state = SpotifyPkce.generateState()
                check(secureStore.putString(KEY_VERIFIER, verifier)) {
                    "Не удалось сохранить code_verifier"
                }
                check(secureStore.putString(KEY_STATE, state)) {
                    "Не удалось сохранить state"
                }
                check(secureStore.putString(KEY_CLIENT_ID, id)) {
                    "Не удалось сохранить Client ID"
                }
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
            migrated.await()
            val parsed = Uri.parse(uri)
            val code = parsed.getQueryParameter("code")
            completionMutex.withLock {
                // Тот же код уже обменян: молча выходим, без второго тоста.
                if (code != null && code == lastCompletedCode) {
                    return@withLock Result.success(Unit)
                }
                val result = runCatching { exchangeCode(parsed) }
                    .rethrowCancellation()
                    .onFailure { error ->
                        Log.w(TAG, "Вход Spotify не завершён: ${error.message}")
                    }
                if (result.isSuccess) lastCompletedCode = code
                eventChannel.trySend(
                    if (result.isSuccess) SpotifyAuthEvent.SignedIn else SpotifyAuthEvent.Failed,
                )
                result
            }
        }

    private suspend fun exchangeCode(parsed: Uri) {
        parsed.getQueryParameter("error")?.let { error ->
            error("Spotify вернул ошибку авторизации: $error")
        }
        val code = parsed.getQueryParameter("code")
            ?: error("В ответе Spotify нет кода авторизации")
        val returnedState = parsed.getQueryParameter("state")
            ?: error("В ответе Spotify нет state")
        val expectedState = secureStore.getString(KEY_STATE)
        if (expectedState == null || expectedState != returnedState) {
            error("state не совпал: ответ пришёл не на наш запрос")
        }
        val verifier = secureStore.getString(KEY_VERIFIER)
            ?: error("Потерян code_verifier — начните вход заново")
        val clientId = secureStore.getString(KEY_CLIENT_ID)
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
        secureStore.remove(KEY_VERIFIER)
        secureStore.remove(KEY_STATE)
        _authState.value = readState()
    }

    override suspend fun accessToken(forceRefresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            migrated.await()
            val refreshToken = secureStore.getString(KEY_REFRESH) ?: return@withContext null
            val cached = secureStore.getString(KEY_ACCESS)
            if (!forceRefresh && cached != null && isFresh()) return@withContext cached

            tokenMutex.withLock {
                val current = secureStore.getString(KEY_ACCESS)
                if (!forceRefresh && current != null && isFresh()) return@withLock current
                val clientId = secureStore.getString(KEY_CLIENT_ID)
                if (clientId.isNullOrBlank()) return@withLock null
                runCatching { refresh(clientId, refreshToken) }
                    .rethrowCancellation()
                    .onFailure { error ->
                        Log.w(TAG, "Токен Spotify не обновился: ${error.message}")
                    }
                    .getOrNull()
            }
        }

    override suspend fun fetchMyPlaylists(): Result<List<SpotifyPlaylistSummary>> =
        withContext(Dispatchers.IO) {
            migrated.await()
            runCatching {
                val token = accessToken() ?: error("Нужен вход в Spotify")
                val body = get("$SPOTIFY_MY_PLAYLISTS_URL?limit=$PLAYLISTS_LIMIT", token)
                parsePlaylists(body)
            }.rethrowCancellation()
                .onFailure { error ->
                    Log.w(TAG, "Плейлисты Spotify не загрузились: ${error.message}")
                }
        }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            migrated.await()
            AUTH_KEYS.forEach(secureStore::remove)
            // Открытые ключи прошлых версий тоже не должны пережить выход.
            legacyPrefs.edit().clear().apply()
            _authState.value = SpotifyAuthState()
        }
    }

    /** Токен ещё жив с запасом в минуту — не тратим запрос на обновление. */
    private fun isFresh(): Boolean =
        System.currentTimeMillis() < secureStore.getLong(KEY_EXPIRES_AT) - TOKEN_MARGIN_MS

    private suspend fun refresh(clientId: String, refreshToken: String): String? {
        rateLimiter.awaitSlot()
        val form = FormBody.Builder()
            .add("grant_type", GRANT_TYPE_REFRESH)
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()
        val body = postForm(SPOTIFY_TOKEN_URL, form)
        saveTokens(body)
        _authState.value = readState()
        return secureStore.getString(KEY_ACCESS)
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
        check(secureStore.putString(KEY_ACCESS, access)) {
            "Не удалось сохранить access_token"
        }
        check(secureStore.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)) {
            "Не удалось сохранить срок токена"
        }
        // При обновлении Spotify может не прислать refresh_token —
        // тогда остаётся прежний.
        if (refresh != null) {
            check(secureStore.putString(KEY_REFRESH, refresh)) {
                "Не удалось сохранить refresh_token"
            }
        }
    }

    private fun loadProfile() {
        val access = secureStore.getString(KEY_ACCESS) ?: return
        val root = json.parseToJsonElement(get(SPOTIFY_ME_URL, access)).jsonObject
        val name = root["display_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: root["id"]?.jsonPrimitive?.contentOrNull
        val image = root["images"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
        secureStore.putString(KEY_NAME, name.orEmpty())
        secureStore.putString(KEY_IMAGE, image.orEmpty())
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
        val connected = !secureStore.getString(KEY_REFRESH).isNullOrBlank() ||
            !secureStore.getString(KEY_ACCESS).isNullOrBlank()
        return SpotifyAuthState(
            connected = connected,
            displayName = secureStore.getString(KEY_NAME)?.takeIf { it.isNotBlank() },
            imageUrl = secureStore.getString(KEY_IMAGE)?.takeIf { it.isNotBlank() },
        )
    }

    /** Быстрая проверка без расшифровки: есть ли вообще сохранённая сессия. */
    private fun hasStoredSession(): Boolean =
        secureStore.contains(KEY_REFRESH) || secureStore.contains(KEY_ACCESS) ||
            legacyPrefs.contains(KEY_REFRESH) || legacyPrefs.contains(KEY_ACCESS)

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

        val AUTH_KEYS = listOf(
            KEY_ACCESS,
            KEY_REFRESH,
            KEY_EXPIRES_AT,
            KEY_VERIFIER,
            KEY_STATE,
            KEY_CLIENT_ID,
            KEY_NAME,
            KEY_IMAGE,
        )

        val LEGACY_KEYS = AUTH_KEYS
    }
}
