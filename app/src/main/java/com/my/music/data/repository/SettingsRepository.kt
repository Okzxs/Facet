package com.my.music.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Пользовательские настройки оформления.
 *
 * @param dynamicCoverColors динамические цвета обложки (палитра плеера от artwork);
 * @param accentColorArgb выбранный вручную акцентный цвет (ARGB), применяется когда динамика выключена.
 */
data class AppSettings(
    val dynamicCoverColors: Boolean = true,
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
) {
    companion object {
        /** Базовый фиолетовый — совпадает с фиксированной палитрой приложения. */
        const val DEFAULT_ACCENT_ARGB: Int = 0xFF7B5EA7.toInt()
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
