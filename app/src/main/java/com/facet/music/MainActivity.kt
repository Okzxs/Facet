package com.facet.music

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.ui.theme.FacetTheme
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SpotifyAuthRepository
import com.facet.music.navigation.AppNavGraph
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Вход Spotify: deep link возвращается из браузера в это же Activity. */
    @Inject
    lateinit var spotifyAuthRepository: SpotifyAuthRepository

    /**
     * Язык, с которым Activity уже создана. Читается синхронно из настроек:
     * `attachBaseContext` выполняется раньше `onCreate`, когда Hilt ещё
     * не отдал репозиторий.
     */
    private var appliedLanguage: String = AppLocale.LANGUAGE_SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLocale.savedLanguage(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Холодный старт по ссылке из браузера: сразу завершаем вход.
        handleSpotifyRedirect(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            // Смена языка пересоздаёт Activity: attachBaseContext заново
            // обернёт контекст нужной локалью, и все строки перечитаются.
            LaunchedEffect(settings.appLanguage) {
                if (settings.appLanguage != appliedLanguage) {
                    appliedLanguage = settings.appLanguage
                    recreate()
                }
            }
            // Android 12+ использует Dynamic Color системы, пока пользователь
            // не задал собственный акцент в настройках (он работает на всех версиях).
            // Ручная палитра из настроек применяется только на старых версиях.
            // Акцент включается в настройках: пока выключен, тема берёт цвета обоев
            // (Dynamic Color), а собственный цвет и legacy-палитра игнорируются.
            val accentOverride = if (
                settings.accentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ) {
                Color(settings.accentColorArgb)
            } else {
                null
            }
            val customAccentOverride = if (settings.accentEnabled) {
                settings.customAccentColor?.let { Color(it) }
            } else {
                null
            }

            FacetTheme(
                themeMode = settings.themeMode,
                accentColor = accentOverride,
                customAccentColor = customAccentOverride,
            ) {
                AppNavGraph()
            }
        }
    }

    /**
     * Activity уже запущена (launchMode=singleTop): браузер возвращает
     * пользователя новым intent'ом, а не новым экземпляром.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyRedirect(intent)
    }

    /** Обмен кода на токены + профиль. Ошибку показываем тостом. */
    private fun handleSpotifyRedirect(intent: Intent?) {
        val data = intent?.dataString ?: return
        if (!spotifyAuthRepository.isRedirect(data)) return
        lifecycleScope.launch {
            spotifyAuthRepository.completeAuthorization(data)
                .onSuccess {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.spotify_signed_in_toast),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.spotify_sign_in_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
}
