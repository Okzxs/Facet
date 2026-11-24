package com.facet.music.feature.settings

import androidx.annotation.StringRes
import com.facet.music.R

data class SettingsSearchEntry(
    @StringRes val titleRes: Int,
    @StringRes val hintRes: Int,
    @StringRes val sectionRes: Int,
    val anchor: String,
    val keywords: List<String>,
    val opensStudio: Boolean = false,
)

fun settingsSearchEntries(): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        titleRes = R.string.settings_studio_title,
        hintRes = R.string.settings_studio_open_hint,
        sectionRes = R.string.settings_appearance,
        anchor = ANCHOR_APPEARANCE,
        opensStudio = true,
        keywords = listOf(
            "тема", "тем", "оформлен", "палитр", "цвет", "акцент", "шрифт", "serif",
            "sans", "угл", "форма", "док", "вкладк", "главн", "секци", "плеер",
            "обложк", "фон", "жест", "прогресс", "мини", "пресет", "стил", "профил",
            "theme", "style", "palette", "color", "accent", "font", "type", "shape",
            "dock", "tabs", "home", "sections", "player", "cover", "gesture",
            "progress", "mini", "layout", "preset", "profile", "json",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_dynamic_cover,
        hintRes = R.string.settings_dynamic_cover_hint,
        sectionRes = R.string.settings_appearance,
        anchor = ANCHOR_APPEARANCE,
        keywords = listOf(
            "обложк", "цвет", "фон", "динамич", "артворк",
            "artwork", "colors", "dynamic", "cover", "tint",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_language_section,
        hintRes = R.string.settings_language_hint,
        sectionRes = R.string.settings_language_section,
        anchor = ANCHOR_LANGUAGE,
        keywords = listOf(
            "язык", "локал", "русск", "перевод интерфейс",
            "language", "locale", "russian", "english",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_lyrics_translation_section,
        hintRes = R.string.settings_lyrics_translation_hint,
        sectionRes = R.string.settings_lyrics_translation_section,
        anchor = ANCHOR_LYRICS_TRANSLATION,
        keywords = listOf(
            "перевод", "текст", "лирик", "субтитр",
            "translation", "lyrics", "subtitles",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_spotify,
        hintRes = R.string.settings_spotify_hint,
        sectionRes = R.string.settings_integrations,
        anchor = ANCHOR_INTEGRATIONS,
        keywords = listOf(
            "spotify", "спотифай", "ключ", "client", "вход", "логин",
            "импорт", "import", "oauth", "интеграц", "integration",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_permissions,
        hintRes = R.string.settings_location_denied_hint,
        sectionRes = R.string.settings_permissions,
        anchor = ANCHOR_PERMISSIONS,
        keywords = listOf(
            "разрешен", "геолок", "уведомл", "permission",
            "location", "notification", "gps",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_background_section,
        hintRes = R.string.settings_focus_resume_hint,
        sectionRes = R.string.settings_background_section,
        anchor = ANCHOR_BACKGROUND,
        keywords = listOf(
            "фон", "фоновое", "воспроизвед", "батаре", "энергосбереж",
            "background", "playback", "battery", "focus",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_quality_title,
        hintRes = R.string.settings_quality_hint,
        sectionRes = R.string.settings_downloads_section,
        anchor = ANCHOR_DOWNLOADS,
        keywords = listOf(
            "качеств", "скачиван", "битрейт", "загрузк",
            "quality", "download", "bitrate",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_cache_title,
        hintRes = R.string.settings_cache_empty,
        sectionRes = R.string.settings_downloads_section,
        anchor = ANCHOR_CACHE,
        keywords = listOf("кэш", "кеш", "очистк", "cache", "clear", "media3"),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_analytics_title,
        hintRes = R.string.settings_analytics_hint,
        sectionRes = R.string.settings_privacy_section,
        anchor = ANCHOR_PRIVACY,
        keywords = listOf(
            "аналитик", "приватн", "данн", "статистик",
            "analytics", "privacy", "data", "stats",
        ),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_support_title,
        hintRes = R.string.settings_support_hint,
        sectionRes = R.string.settings_privacy_section,
        anchor = ANCHOR_SUPPORT,
        keywords = listOf("поддержк", "помощ", "связ", "support", "help", "telegram"),
    ),
    SettingsSearchEntry(
        titleRes = R.string.settings_licenses,
        hintRes = R.string.settings_about_hint,
        sectionRes = R.string.settings_privacy_section,
        anchor = ANCHOR_ABOUT,
        keywords = listOf(
            "лиценз", "верси", "gpl", "открыт", "about",
            "license", "version", "open source",
        ),
    ),
)

fun SettingsSearchEntry.matches(query: String, title: String, hint: String): Boolean {
    if (query.isBlank()) return false
    val normalized = query.trim().lowercase()
    if (title.lowercase().contains(normalized)) return true
    if (hint.lowercase().contains(normalized)) return true
    return keywords.any { it.contains(normalized) }
}

const val ANCHOR_APPEARANCE = "appearance"
const val ANCHOR_LANGUAGE = "language"
const val ANCHOR_LYRICS_TRANSLATION = "lyrics_translation"
const val ANCHOR_INTEGRATIONS = "integrations"
const val ANCHOR_PERMISSIONS = "permissions"
const val ANCHOR_BACKGROUND = "background"
const val ANCHOR_DOWNLOADS = "downloads"
const val ANCHOR_CACHE = "cache"
const val ANCHOR_PRIVACY = "privacy"
const val ANCHOR_SUPPORT = "support"
const val ANCHOR_ABOUT = "about"
