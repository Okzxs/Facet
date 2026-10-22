package com.my.music.core.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Squircle-форма: суперэллипс вместо обычного скруглённого прямоугольника.
 * Даёт более «плотные» и выразительные углы, характерные для MD3 Expressive.
 */
val SquircleShape: Shape = GenericShape { size, _ ->
    if (size.width > 0f && size.height > 0f) {
        val exponent = 3.6f
        val steps = 56
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        for (i in 0..steps) {
            val angle = 2.0 * PI * i / steps
            val cosValue = cos(angle).toFloat()
            val sinValue = sin(angle).toFloat()
            val x = centerX + centerX * sign(cosValue) * abs(cosValue).pow(2f / exponent)
            val y = centerY + centerY * sign(sinValue) * abs(sinValue).pow(2f / exponent)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Скругления по умолчанию для всех M3-компонентов. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Форма-пилюля для чипсов, бейджей и компактных действий. */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** Крупная выразительная форма для hero-блоков. */
val HeroShape: Shape = RoundedCornerShape(32.dp)

/** Форма карточек Bento-сетки. */
val BentoCardShape: Shape = RoundedCornerShape(30.dp)
