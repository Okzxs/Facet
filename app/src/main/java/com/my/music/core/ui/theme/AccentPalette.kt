package com.my.music.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Вариант акцентного цвета для ручного выбора в настройках.
 */
data class AccentOption(
    val name: String,
    val color: Color,
)

/**
 * Базовые акцентные цвета (первый — фиолетовый, совпадает с дефолтом приложения).
 */
val AccentOptions = listOf(
    AccentOption("Фиолетовый", Color(0xFF7B5EA7)),
    AccentOption("Синий", Color(0xFF3F6BD8)),
    AccentOption("Бирюзовый", Color(0xFF0F9B8E)),
    AccentOption("Зелёный", Color(0xFF43A047)),
    AccentOption("Оранжевый", Color(0xFFE07B2E)),
    AccentOption("Красный", Color(0xFFC94F4A)),
)

/** Найти вариант по сохранённому ARGB. */
fun accentOptionFor(argb: Int): AccentOption? =
    AccentOptions.firstOrNull { it.color.toArgb() == argb }
