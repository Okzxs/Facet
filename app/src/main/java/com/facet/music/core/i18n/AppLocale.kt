package com.facet.music.core.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocale {

    const val PREFS_NAME = "facet_settings"

    const val KEY_APP_LANGUAGE = "app_language"

    const val LANGUAGE_SYSTEM = "system"

    const val LANGUAGE_RU = "ru"

    const val LANGUAGE_EN = "en"

    val supportedLanguages = listOf(LANGUAGE_SYSTEM, LANGUAGE_RU, LANGUAGE_EN)

    fun savedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_APP_LANGUAGE, null)
        return value?.takeIf { it in supportedLanguages } ?: LANGUAGE_SYSTEM
    }

    fun wrap(base: Context, language: String): Context {
        if (language == LANGUAGE_SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
