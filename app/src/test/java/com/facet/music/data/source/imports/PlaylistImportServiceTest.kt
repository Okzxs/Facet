package com.facet.music.data.source.imports

import com.facet.music.core.import.ImportError
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SearchCacheRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SpotifyAuthRepository
import com.facet.music.data.source.external.SpotifyProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistImportServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun service(spotifyConfigured: Boolean = false): PlaylistImportService {
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns flowOf(
            AppSettings(
                spotifyClientId = if (spotifyConfigured) "client-id" else "",
                spotifyClientSecret = if (spotifyConfigured) "client-secret" else "",
            ),
        )
        val auth = mockk<SpotifyAuthRepository>(relaxed = true)
        every { auth.authState } returns MutableStateFlow(SpotifyAuthState())
        val spotify = SpotifyProvider(
            client = OkHttpClient(),
            json = json,
            settingsRepository = settings,
            spotifyAuth = auth,
            searchCache = mockk<SearchCacheRepository>(relaxed = true),
            rateLimiter = RateLimiter(),
        )
        return PlaylistImportServiceImpl(
            client = OkHttpClient(),
            json = json,
            spotifyProvider = spotify,
            deezerRateLimiter = RateLimiter(),
        )
    }

    private fun failure(result: PlaylistImportResult): PlaylistImportResult.Failure =
        result as? PlaylistImportResult.Failure
            ?: throw AssertionError("Ожидалась ошибка импорта, получено: $result")

    @Test
    fun `пустая ссылка не идёт в сеть`() = runTest {
        val result = failure(service().load(""))

        assertEquals(ImportError.EMPTY_LINK, result.error)
    }

    @Test
    fun `мусорная ссылка даёт unsupported`() = runTest {
        val result = failure(service().load("https://example.com/playlist/42"))

        assertEquals(ImportError.UNSUPPORTED_LINK, result.error)
    }

    @Test
    fun `яндекс музыка честно сообщает о недоступности`() = runTest {
        val result = failure(
            service().load("https://music.yandex.ru/users/souler/playlists/1034"),
        )

        assertEquals(ImportError.YANDEX_UNSUPPORTED, result.error)
    }

    @Test
    fun `apple music больше не импортируется`() = runTest {
        val result = failure(
            service().load("https://music.apple.com/ru/playlist/my-favourites/pl.u-1a2b3c4d"),
        )

        assertEquals(ImportError.UNSUPPORTED_LINK, result.error)
    }

    @Test
    fun `spotify без ключей просит добавить их в настройках`() = runTest {
        val result = failure(
            service(spotifyConfigured = false)
                .load("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        )

        assertEquals(ImportError.SPOTIFY_NOT_CONFIGURED, result.error)
    }
}
