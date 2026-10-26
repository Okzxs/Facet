package com.my.music.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Пользовательские настройки оформления.
 *
 * @param dynamicCoverColors динамические цвета обложки (палитра плеера от artwork);
 * @param accentColorArgb выбранный вручную акцентный цвет (ARGB), применяется на Android ниже 12.
 */
data class AppSettings(
    val dynamicCoverColors: Boolean = false,
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
) {
    companion object {
        /** Базовый коралловый — совпадает с fallback-палитрой приложения. */
        const val DEFAULT_ACCENT_ARGB: Int = 0xFFA63A2B.toInt()
    }
}

/**
 * Репозиторий настроек приложения (SharedPreferences).
 */
interface SettingsRepository {

    /** Реактивный поток текущих настроек. */
    val settings: Flow<AppSettings>

    /** Включить/выключить динамические цвета обложки. */
    suspend fun setDynamicCoverColors(enabled: Boolean)

    /** Сохранить акцентный цвет (ARGB). */
    suspend fun setAccentColorArgb(argb: Int)
}
