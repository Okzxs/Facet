package com.my.music.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Режим темы приложения.
 */
enum class ThemeMode {
    /** Всегда светлая. */
    LIGHT,

    /** Всегда тёмная. */
    DARK,

    /** Следует системной теме. */
    SYSTEM,
}

/**
 * Пользовательские настройки оформления.
 *
 * @param dynamicCoverColors динамические цвета обложки (палитра плеера от artwork);
 * @param accentColorArgb выбранный вручную акцентный цвет (ARGB), применяется на Android ниже 12;
 * @param themeMode режим темы: светлая/тёмная/системная;
 * @param customAccentColor собственный акцентный цвет пользователя (ARGB) — работает
 * на всех версиях Android и имеет приоритет над Dynamic Color; null — не задан;
 * @param recentAccentColors недавно применённые акцентные цвета (ARGB), свежие в
 * начале списка, без дубликатов, не больше [AppSettings.MAX_RECENT_ACCENTS].
 */
data class AppSettings(
    val dynamicCoverColors: Boolean = false,
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customAccentColor: Int? = null,
    val recentAccentColors: List<Int> = emptyList(),
    /** Включён ли собственный акцентный цвет; выключен — цвета берутся с обоев. */
    val accentEnabled: Boolean = false,
) {
    companion object {
        /** Базовый коралловый — совпадает с fallback-палитрой приложения. */
        const val DEFAULT_ACCENT_ARGB: Int = 0xFFA63A2B.toInt()

        /** Сколько недавних цветов держим в настройках и показываем в UI. */
        const val MAX_RECENT_ACCENTS: Int = 5
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

    /** Сохранить режим темы. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** Сохранить собственный акцентный цвет; null сбрасывает выбор. */
    suspend fun setCustomAccentColor(argb: Int?)

    /** Включить/выключить собственный акцент; выключенный — цвета обоев. */
    suspend fun setAccentEnabled(enabled: Boolean)

    /**
     * Добавить цвет в недавние акценты: свежий уходит в начало (LIFO),
     * дубликаты убираются, список обрезается до [AppSettings.MAX_RECENT_ACCENTS].
     */
    suspend fun addRecentAccentColor(argb: Int)
}
