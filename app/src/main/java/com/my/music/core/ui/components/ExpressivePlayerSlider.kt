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

/**
 * Слайдер прогресса плеера: сплошная скруглённая дорожка 8dp
 * (трек → буфер → проигранная часть) с ползунком-пилюлей.
 * При захвате ползунок пружинно раздувается (bounce с овершутом),
 * под ним раскрывается ореол акцентного цвета.
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
        val trackHeight = 8.dp.toPx()
        val trackRadius = trackHeight / 2f

        // Базовая дорожка (общая длительность)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(pad, centerY - trackHeight / 2f),
            size = Size(span, trackHeight),
            cornerRadius = CornerRadius(trackRadius),
        )

        // Слой буферизации — полупрозрачный акцент
        if (buffer > 0f) {
            drawRoundRect(
                color = activeColor.copy(alpha = 0.28f),
                topLeft = Offset(pad, centerY - trackHeight / 2f),
                size = Size((span * buffer).coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = CornerRadius(trackRadius),
            )
        }

        // Проигранная часть — плотный акцент
        if (displayFraction > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(pad, centerY - trackHeight / 2f),
                size = Size((span * displayFraction).coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = CornerRadius(trackRadius),
            )
        }

        val activeX = pad + span * displayFraction

        // Ореол под бегунком — раскрывается вместе с пружинным отскоком
        if (bounce > 0.02f) {
            drawCircle(
                color = activeColor.copy(alpha = (0.22f * bounce).coerceIn(0f, 1f)),
                radius = (10f + 16f * bounce).dp.toPx(),
                center = Offset(activeX, centerY),
            )
        }

        // Ползунок-пилюля: пружинное увеличение при захвате
        val safeBounce = bounce.coerceIn(-0.3f, 1.4f)
        val thumbW = 10.dp.toPx() * (1f + 0.35f * safeBounce)
        val thumbH = 30.dp.toPx() * (1f + 0.16f * safeBounce)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(activeX - thumbW / 2f, centerY - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f),
        )
    }
}
