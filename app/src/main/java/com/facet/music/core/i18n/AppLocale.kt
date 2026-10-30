package com.facet.music.core.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Язык интерфейса без AppCompat.
 *
 * `androidx.appcompat` в проекте нет, а версии зависимостей трогать нельзя,
 * поэтому `AppCompatDelegate.setApplicationLocales` недоступен. Вместо него
 * `MainActivity` переопределяет `attachBaseContext` и оборачивает базовый
 * контекст через [wrap]: все строки (`stringResource`) и ресурсы экрана
 * разрешаются уже в нужной локали. Смена языка применяется пересозданием
 * Activity, поэтому перезапуск приложения не требуется.
 */
object AppLocale {

    /** Файл настроек приложения: тот же, что у `SettingsRepositoryImpl`. */
    const val PREFS_NAME = "facet_settings"

    /** Ключ языка в [PREFS_NAME]. */
    const val KEY_APP_LANGUAGE = "app_language"

    /** «Как в системе» — значение по умолчанию. */
    const val LANGUAGE_SYSTEM = "system"

    /** Принудительный русский. */
    const val LANGUAGE_RU = "ru"

    /** Принудительный английский. */
    const val LANGUAGE_EN = "en"

    /** Все значения, которые понимает настройка. */
    val supportedLanguages = listOf(LANGUAGE_SYSTEM, LANGUAGE_RU, LANGUAGE_EN)

    /**
     * Язык, сохранённый в настройках, читается синхронно: `attachBaseContext`
     * вызывается раньше `onCreate`, когда Hilt ещё не отдал репозиторий.
     * Неизвестное или битое значение трактуется как «как в системе».
     */
    fun savedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_APP_LANGUAGE, null)
        return value?.takeIf { it in supportedLanguages } ?: LANGUAGE_SYSTEM
    }

    /**
     * Оборачивает контекст локализованной конфигурацией.
     * Для [LANGUAGE_SYSTEM] контекст возвращается как есть — язык берётся
     * из системы, и Android сам пересоздаёт Activity при его смене.
     */
    fun wrap(base: Context, language: String): Context {
        if (language == LANGUAGE_SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        // Дату и числа форматируем тем же языком, что и интерфейс.
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
