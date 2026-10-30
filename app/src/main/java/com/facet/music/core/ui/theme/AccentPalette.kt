package com.facet.music.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.facet.music.R

/**
 * Вариант акцентного цвета для ручного выбора в настройках.
 * Название хранится ссылкой на строковый ресурс: подпись для скринридера
 * должна переводиться вместе с интерфейсом.
 */
data class AccentOption(
    @StringRes val nameRes: Int,
    val color: Color,
)

/**
 * Базовые акцентные цвета для Android ниже 12 (первый совпадает с fallback-палитрой).
 */
val AccentOptions = listOf(
    AccentOption(R.string.settings_accent_coral, Color(0xFFA63A2B)),
    AccentOption(R.string.settings_accent_purple, Color(0xFF7B5EA7)),
    AccentOption(R.string.settings_accent_blue, Color(0xFF3F6BD8)),
    AccentOption(R.string.settings_accent_teal, Color(0xFF0F9B8E)),
    AccentOption(R.string.settings_accent_green, Color(0xFF43A047)),
    AccentOption(R.string.settings_accent_orange, Color(0xFFE07B2E)),
    AccentOption(R.string.settings_accent_red, Color(0xFFC94F4A)),
)

/** Найти вариант по сохранённому ARGB. */
fun accentOptionFor(argb: Int): AccentOption? =
    AccentOptions.firstOrNull { it.color.toArgb() == argb }
