package com.my.music.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Полный оборот фазы волны (радианы). */
private const val TWO_PI = 6.283185307179586

/**
 * Слайдер прогресса «волна»: анимированная синусоида вместо прямой дорожки.
 * Проигранная часть слева подсвечена акцентом, зона буферизации — чуть плотнее.
 * На паузе волна плавно выпрямляется в линию, бегунок — точка
 * с пружинным раздутием и ореолом при захвате.
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
    isPlaying: Boolean = true,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }

    // Точка и ореол пружинят при захвате
    val bounce by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.30f, stiffness = Spring.StiffnessLow),
        label = "expressiveSliderBounce",
    )

    // При паузе волна плавно выпрямляется в прямую линию
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "waveAmplitude",
    )

    // Бегущая фаза синусоиды: живёт только во время воспроизведения.
    // Значение читается в фазе отрисовки — без рекомпозиций каждый кадр
    val wavePhase: State<Float>? = if (isPlaying) {
        rememberInfiniteTransition(label = "wavePhase").animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhaseValue",
        )
    } else {
        null
    }

    val displayFraction = (if (isDragging) dragValue else value).coerceIn(0f, 1f)
    val bufferFraction = bufferedFraction.coerceIn(0f, 1f)

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
        val spanD = span.toDouble()
        val centerY = size.height / 2f
        val progressX = pad + span * displayFraction
        val amp = 9.dp.toPx() * amplitude
        val phase = (wavePhase?.value ?: 0f).toDouble()
        val wavelength = 44.dp.toPx().toDouble()
        val stroke = 3.dp.toPx()

        // Синусоида с сужением к краям — волна живёт в центре дорожки
        val wave = Path()
        wave.moveTo(pad, centerY)
        val steps = 96
        for (i in 1..steps) {
            val u = i / steps.toDouble()
            val x = pad + (spanD * u).toFloat()
            val taper = sin(PI * u)
            val angle = TWO_PI * (spanD * u / wavelength) + phase
            wave.lineTo(x, centerY - (amp * taper * sin(angle)).toFloat())
        }

        // Бегунок едет по самой волне: та же формула синуса в его X-координате
        val thumbU = displayFraction.toDouble()
        val thumbAngle = TWO_PI * (spanD * thumbU / wavelength) + phase
        val thumbY = centerY - (amp * sin(PI * thumbU) * sin(thumbAngle)).toFloat()

        // Тусклая волна — вся длительность трека
        drawPath(wave, trackColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Зона буферизации — чуть плотнее дорожка
        if (bufferFraction > 0.01f) {
            clipRect(left = 0f, right = pad + span * bufferFraction) {
                drawPath(
                    wave,
                    trackColor.copy(alpha = (trackColor.alpha * 1.8f).coerceAtMost(1f)),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        // Проигранная часть — акцентная волна слева направо
        clipRect(left = 0f, right = progressX) {
            drawPath(wave, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }

        // Ореол под бегунком — раскрывается вместе с пружиной
        if (bounce > 0.02f) {
            drawCircle(
                color = activeColor.copy(alpha = (0.22f * bounce).coerceIn(0f, 1f)),
                radius = (10f + 16f * bounce).dp.toPx(),
                center = Offset(progressX, thumbY),
            )
        }

        // Бегунок-точка: пружинное увеличение при захвате
        val safeBounce = bounce.coerceIn(-0.3f, 1.4f)
        drawCircle(
            color = thumbColor,
            radius = 5.dp.toPx() * (1f + 0.4f * safeBounce),
            center = Offset(progressX, thumbY),
        )
    }
}
