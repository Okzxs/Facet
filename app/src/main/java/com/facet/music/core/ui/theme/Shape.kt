package com.facet.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Шкала Material 3 Expressive: 4 — мелкие элементы, 8 — чипсы,
 * 12 — карточки, 16 — крупные блоки, 28 — плеер и островки.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Форма-пилюля для кнопок, чипсов и индикатора навигации. */
val PillShape: Shape = RoundedCornerShape(percent = 50)
