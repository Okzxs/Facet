package com.my.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import com.my.music.core.ui.theme.MyMusicTheme
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import com.my.music.navigation.AppNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            // Динамика обложки выключена — фиксируем акцент (по умолчанию фиолетовый);
            // включена — плеер берёт палитру обложки, общая тема остаётся фиолетовой
            val accentOverride = if (settings.dynamicCoverColors) null else Color(settings.accentColorArgb)

            MyMusicTheme(accentColor = accentOverride) {
                AppNavGraph()
            }
        }
    }
}
