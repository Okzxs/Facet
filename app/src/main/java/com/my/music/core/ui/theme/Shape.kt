package com.my.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Единая шкала форм Material 3: 4 / 8 / 12 / 16 / 24 плюс полная пилюля.
 * Весь интерфейс берёт радиусы отсюда — отдельных «магических» dp больше нет.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Форма-пилюля для чипсов, бейджей и компактных действий. */
val PillShape: Shape = RoundedCornerShape(percent = 50)
