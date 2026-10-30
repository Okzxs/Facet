package com.my.music

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import com.my.music.core.i18n.AppLocale
import com.my.music.core.ui.theme.MyMusicTheme
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import com.my.music.navigation.AppNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

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

            MyMusicTheme(
                themeMode = settings.themeMode,
                accentColor = accentOverride,
                customAccentColor = customAccentOverride,
            ) {
                AppNavGraph()
            }
        }
    }
}
