package com.facet.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Строгая шкала Material 3: 12 — мелкие элементы, 16 — карточки,
 * 24 — крупные блоки и плеер. Асимметрии и «магических» радиусов нет.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Форма-пилюля для кнопок, чипсов и индикатора навигации. */
val PillShape: Shape = RoundedCornerShape(percent = 50)
