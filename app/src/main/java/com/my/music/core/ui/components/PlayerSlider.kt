package com.my.music.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Expressive-слайдер прогресса на чистом Compose Canvas.
 * - Толстая скруглённая дорожка (8dp): проигранная часть окрашивается в [activeColor]
 * - Буферизация — полупрозрачный слой поверх дорожки
 * - Ползунок — вертикальная пилюля, пружинно увеличивается при перетаскивании
 * Без бесконечных анимаций: рисуется только при изменениях.
 */
@Composable
fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    activeColor: Color,
    trackColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    bufferedFraction: Float = 0f,
    enabled: Boolean = true,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }

    // Пружинное увеличение ползунка при перетаскивании
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "playerSliderThumbScale",
    )

    val displayFraction = (if (isDragging) dragValue else value).coerceIn(0f, 1f)
    val buffer = bufferedFraction.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished(dragValue)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragValue = (dragValue + dragAmount / size.width).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(fraction)
                    onValueChangeFinished(fraction)
                }
            },
    ) {
        val trackHeight = 8.dp.toPx()
        val thumbWidth = 10.dp.toPx()
        val thumbHeight = 30.dp.toPx()
        val edge = thumbWidth / 2f
        val startX = edge
        val endX = size.width - edge
        val centerY = size.height / 2f
        val trackWidth = (endX - startX).coerceAtLeast(1f)

        // Дорожка целиком (длительность трека) — без серых плашек, полупрозрачная линия
        drawLine(
            color = trackColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )

        // Буферизация — полупрозрачный слой поверх фоновой дорожки
        if (buffer > 0f) {
            drawLine(
                color = activeColor.copy(alpha = 0.28f),
                start = Offset(startX, centerY),
                end = Offset(startX + trackWidth * buffer, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
        }

        // Проигранная часть — акцентный цвет
        val activeX = startX + trackWidth * displayFraction
        if (displayFraction > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(startX, centerY),
                end = Offset(activeX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
        }

        // Ореол под ползунком (проявляется при перетаскивании вместе с масштабом)
        val haloAlpha = ((thumbScale - 1f) / 0.35f).coerceIn(0f, 1f) * 0.30f
        if (haloAlpha > 0f) {
            drawCircle(
                color = activeColor.copy(alpha = haloAlpha),
                radius = 22.dp.toPx(),
                center = Offset(activeX, centerY),
            )
        }

        // Ползунок — вертикальная пилюля
        val currentThumbWidth = thumbWidth * thumbScale
        val currentThumbHeight = thumbHeight * thumbScale
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(activeX - currentThumbWidth / 2f, centerY - currentThumbHeight / 2f),
            size = Size(currentThumbWidth, currentThumbHeight),
            cornerRadius = CornerRadius(currentThumbWidth / 2f),
        )
    }
}
