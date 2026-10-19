package com.my.music.data.local

import android.content.Context
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
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
        dynamicCoverColors = prefs.getBoolean(KEY_DYNAMIC_COVER_COLORS, true),
        accentColorArgb = prefs.getInt(KEY_ACCENT_COLOR, AppSettings.DEFAULT_ACCENT_ARGB),
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

    private companion object {
        const val PREFS_NAME = "mymusic_settings"
        const val KEY_DYNAMIC_COVER_COLORS = "dynamic_cover_colors"
        const val KEY_ACCENT_COLOR = "accent_color"
    }
}
