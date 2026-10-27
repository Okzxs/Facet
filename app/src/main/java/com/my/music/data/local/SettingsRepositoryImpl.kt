package com.my.music.data.local

import android.content.Context
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import com.my.music.data.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация настроек на SharedPreferences:
 * значения читаются синхронно при создании, запись — в IO-диспетчере.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    override val settings: Flow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        // По умолчанию — фиолетовая тема, динамика обложки выключена
        dynamicCoverColors = prefs.getBoolean(KEY_DYNAMIC_COVER_COLORS, false),
        accentColorArgb = prefs.getInt(KEY_ACCENT_COLOR, AppSettings.DEFAULT_ACCENT_ARGB),
        themeMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        customAccentColor = if (prefs.contains(KEY_CUSTOM_ACCENT)) {
            prefs.getInt(KEY_CUSTOM_ACCENT, 0)
        } else {
            null
        },
    )

    override suspend fun setDynamicCoverColors(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COVER_COLORS, enabled).apply()
        }
        _settings.update { it.copy(dynamicCoverColors = enabled) }
    }

    override suspend fun setAccentColorArgb(argb: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_ACCENT_COLOR, argb).apply()
        }
        _settings.update { it.copy(accentColorArgb = argb) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
        _settings.update { it.copy(themeMode = mode) }
    }

    override suspend fun setCustomAccentColor(argb: Int?) {
        withContext(Dispatchers.IO) {
            if (argb == null) {
                prefs.edit().remove(KEY_CUSTOM_ACCENT).apply()
            } else {
                prefs.edit().putInt(KEY_CUSTOM_ACCENT, argb).apply()
            }
        }
        _settings.update { it.copy(customAccentColor = argb) }
    }

    private companion object {
        const val PREFS_NAME = "mymusic_settings"
        const val KEY_DYNAMIC_COVER_COLORS = "dynamic_cover_colors"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CUSTOM_ACCENT = "custom_accent_color"
    }
}
