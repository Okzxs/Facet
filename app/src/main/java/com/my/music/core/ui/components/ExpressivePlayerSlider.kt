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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * «Цепочка точек» — фирменный Expressive-слайдер прогресса:
 * - пройденные точки крупнее и ярче, буферные — полупрозрачные, будущие — тусклые;
 * - ближайшая к бегунку точка плавно раздувается;
 * - ползунок-пилюля пружинно «подпрыгивает» при захвате (bounce с овершутом),
 *   ореол проявляется вместе с отскоком.
 * Без бесконечных анимаций: рисуется только при изменениях.
 */
@Composable
fun ExpressivePlayerSlider(
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

    // Пружина-«отскок»: dampingRatio 0.30 даёт заметный овершут — ползунок пружинит
    val bounce by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = Spring.StiffnessLow),
        label = "expressiveSliderBounce",
    )

    val displayFraction = (if (isDragging) dragValue else value).coerceIn(0f, 1f)
    val buffer = bufferedFraction.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val padPx = 8.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val span = (size.width - padPx * 2f).coerceAtLeast(1f)
                        dragValue = ((offset.x - padPx) / span).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished(dragValue)
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val span = (size.width - padPx * 2f).coerceAtLeast(1f)
                        dragValue = (dragValue + dragAmount / span).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val padPx = 8.dp.toPx()
                detectTapGestures { offset ->
                    val span = (size.width - padPx * 2f).coerceAtLeast(1f)
                    val fraction = ((offset.x - padPx) / span).coerceIn(0f, 1f)
                    onValueChange(fraction)
                    onValueChangeFinished(fraction)
                }
            },
    ) {
        val pad = 8.dp.toPx()
        val span = (size.width - pad * 2f).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val spacing = 13.dp.toPx()
        val dotCount = (span / spacing).roundToInt().coerceAtLeast(2) + 1
        val step = span / (dotCount - 1)
        val progressPos = displayFraction * (dotCount - 1)
        val bufferPos = buffer * (dotCount - 1)
        val passedRadius = 3.1.dp.toPx()
        val idleRadius = 2.4.dp.toPx()

        // Цепочка точек от края до края: пройденные — заметно крупнее
        for (i in 0 until dotCount) {
            val proximity = (1f - abs(progressPos - i)).coerceIn(0f, 1f)
            val radius = when {
                i <= progressPos - 0.6f -> passedRadius
                proximity > 0f -> lerp(passedRadius, 4.6.dp.toPx(), proximity)
                i.toFloat() <= bufferPos -> idleRadius + 0.4.dp.toPx()
                else -> idleRadius
            }
            val color = when {
                i <= progressPos - 0.6f || proximity > 0f -> activeColor
                i.toFloat() <= bufferPos -> activeColor.copy(alpha = 0.35f)
                else -> trackColor
            }
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(pad + i * step, centerY),
            )
        }

        val activeX = pad + span * displayFraction

        // Ореол под бегунком — раскрывается вместе с пружинным отскоком
        if (bounce > 0.02f) {
            drawCircle(
                color = activeColor.copy(alpha = (0.22f * bounce).coerceIn(0f, 1f)),
                radius = (8f + 14f * bounce.coerceIn(0f, 1.4f)).dp.toPx(),
                center = Offset(activeX, centerY),
            )
        }

        // Ползунок-пилюля: «сквош» — шире и чуть выше при захвате
        val safeBounce = bounce.coerceIn(-0.3f, 1.4f)
        val thumbW = 8.dp.toPx() * (1f + 0.55f * safeBounce)
        val thumbH = 26.dp.toPx() * (1f + 0.16f * safeBounce)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(activeX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f),
        )
    }
}
