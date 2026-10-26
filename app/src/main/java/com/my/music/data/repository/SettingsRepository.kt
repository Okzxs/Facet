package com.my.music.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Пользовательские настройки оформления и плеера.
 *
 * @param dynamicCoverColors динамические цвета обложки (палитра плеера от artwork);
 * @param accentColorArgb выбранный вручную акцентный цвет (ARGB), применяется на Android ниже 12;
 * @param visualizerDelayMs калибровка задержки волны плеера относительно звука, мс.
 */
data class AppSettings(
    val dynamicCoverColors: Boolean = false,
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
    val visualizerDelayMs: Int = 0,
) {
    companion object {
        /** Базовый коралловый — совпадает с fallback-палитрой приложения. */
        const val DEFAULT_ACCENT_ARGB: Int = 0xFFA63A2B.toInt()

        /** Границы ползунка калибровки задержки. */
        const val VISUALIZER_DELAY_MIN_MS: Int = -200
        const val VISUALIZER_DELAY_MAX_MS: Int = 200
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

    /** Сохранить калибровку задержки волны в миллисекундах. */
    suspend fun setVisualizerDelayMs(delayMs: Int)
}
