package com.my.music.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Полный оборот фазы волны (радианы). */
private const val TWO_PI = 6.283185307179586

/** Finish идёт через тот же FIFO, что Stop/Cancel: без гонки отдельных coroutine. */
private object SliderFinishInteraction : androidx.compose.foundation.interaction.Interaction

/** Штатный Slider сохраняет клавиатуру, semantics, RTL и минимальную зону 48dp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onDragInteraction: (DragInteraction) -> Unit,
    activeColor: Color,
    trackColor: Color,
    thumbColor: Color,
    energy: State<Float>,
    modifier: Modifier = Modifier,
    bufferedFraction: Float = 0f,
    enabled: Boolean = true,
    isPlaying: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactionCallback by rememberUpdatedState(onDragInteraction)
    val finishCallback by rememberUpdatedState(onValueChangeFinished)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction -> interactionCallback(interaction)
                SliderFinishInteraction -> finishCallback()
            }
        }
    }
    val phase = rememberPlaybackPhase(active = isPlaying, periodMs = 1600)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = SliderDefaults.colors(thumbColor = thumbColor, activeTrackColor = activeColor, inactiveTrackColor = trackColor)
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = { scope.launch { interactionSource.emit(SliderFinishInteraction) } },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled) },
        track = { state ->
            // Material3 сам отступает на половину thumb и центрирует оба элемента.
            // Своя дорожка не добавляет горизонтальных padding и не перемещает thumb по Y.
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val fraction = state.value.coerceIn(0f, 1f)
                val bufferEnd = trackX(bufferedFraction.coerceAtLeast(fraction), size.width, rtl)
                val origin = trackX(0f, size.width, rtl)
                val centerY = size.height / 2f
                val inactive = if (enabled) trackColor else trackColor.copy(alpha = 0.12f)
                val active = if (enabled) activeColor else activeColor.copy(alpha = 0.38f)
                val stroke = 3.dp.toPx()
                drawLine(inactive, Offset(0f, centerY), Offset(size.width, centerY), stroke, StrokeCap.Round)
                drawLine(inactive.copy(alpha = (inactive.alpha * 1.8f).coerceAtMost(1f)), Offset(origin, centerY), Offset(bufferEnd, centerY), stroke, StrokeCap.Round)
                val length = size.width * fraction
                val amp = 9.dp.toPx() * energy.value.coerceIn(0f, 1f)
                val wave = Path()
                for (i in 0..96) {
                    val u = i / 96f
                    val x = trackX(fraction * u, size.width, rtl)
                    val y = centerY + amp * sin(PI * u).toFloat() * sin(TWO_PI * length * u / 44.dp.toPx() - phase.value).toFloat()
                    if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
                }
                if (fraction > 0f) drawPath(wave, active, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        },
    )
}
