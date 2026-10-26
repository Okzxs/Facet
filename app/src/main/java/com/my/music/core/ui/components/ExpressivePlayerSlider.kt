package com.my.music.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Высота холста дорожки. Линия 2.5–3dp плюс мягкое свечение от баса
 * укладываются в 24dp, остальное — запас Material3 под касание.
 */
private val TRACK_HEIGHT = 24.dp

/**
 * Круглый бегунок. Ширина задаёт отступ дорожки от краёв: Material3
 * сдвигает `track` на половину thumb и центрирует оба элемента.
 */
private val THUMB_SIZE = 20.dp

/** Базовая толщина линии и добавка от баса: 2.5dp → 3dp. */
private val TRACK_STROKE = 2.5.dp
private val TRACK_STROKE_BOOST = 0.5.dp

/** Finish идёт через тот же FIFO, что Stop/Cancel: без гонки отдельных coroutine. */
private object SliderFinishInteraction : Interaction

/**
 * Таймлайн плеера: штатный Material3 [Slider] отвечает за жесты, drag-cancel,
 * клавиатуру, semantics и RTL, а вся отрисовка живёт в слоте `track`.
 *
 * Визуал — спокойная линия Phase B: пропетанная часть сплошная `primary`,
 * непропетанная — серая, буфер чуть плотнее. Басовый импульс не меняет форму
 * линии: он слегка утолщает заливку (2.5→3dp) и добавляет мягкое свечение.
 * На паузе линия за 500 мс успокаивается до базовой толщины.
 */
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction -> interactionCallback(interaction)
                SliderFinishInteraction -> finishCallback()
            }
        }
    }

    // Пауза: бас перестаёт подмешиваться, и линия плавно возвращается к покою.
    val calm by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "sliderCalm",
    )

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colors = SliderDefaults.colors(
        thumbColor = thumbColor,
        activeTrackColor = activeColor,
        inactiveTrackColor = trackColor,
    )

    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = { scope.launch { interactionSource.emit(SliderFinishInteraction) } },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        thumb = { SliderThumb(interactionSource, thumbColor, activeColor) },
        track = { state ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT),
            ) {
                // Энергия и «спокойствие» читаются в фазе отрисовки — без рекомпозиций.
                val live = energy.value.coerceIn(0f, 1f) * calm.coerceIn(0f, 1f)
                val fraction = state.value.coerceIn(0f, 1f)
                val buffer = bufferedFraction.coerceIn(0f, 1f)
                val centerY = size.height / 2f
                val start = Offset(trackX(0f, size.width, rtl), centerY)
                val end = Offset(trackX(1f, size.width, rtl), centerY)
                val progress = Offset(trackX(fraction, size.width, rtl), centerY)

                val inactive = if (enabled) trackColor else trackColor.copy(alpha = 0.12f)
                val active = if (enabled) activeColor else activeColor.copy(alpha = 0.38f)
                val inactiveStroke = TRACK_STROKE.toPx()
                val activeStroke = (TRACK_STROKE + TRACK_STROKE_BOOST * live).toPx()

                // Непропетанная часть — ровная серая линия
                drawLine(inactive, start, end, inactiveStroke, StrokeCap.Round)

                // Буфер — та же линия, чуть плотнее
                if (buffer > 0.01f) {
                    drawLine(
                        color = inactive.copy(alpha = (inactive.alpha * 1.8f).coerceAtMost(1f)),
                        start = start,
                        end = Offset(trackX(buffer, size.width, rtl), centerY),
                        strokeWidth = inactiveStroke,
                        cap = StrokeCap.Round,
                    )
                }

                if (fraction > 0f) {
                    // Свечение от баса: два слоя поверх линии, форма не меняется
                    if (live > 0.01f) {
                        drawLine(
                            color = active.copy(alpha = 0.10f * live),
                            start = start,
                            end = progress,
                            strokeWidth = activeStroke + 7.dp.toPx() * live,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = active.copy(alpha = 0.14f * live),
                            start = start,
                            end = progress,
                            strokeWidth = activeStroke + 3.dp.toPx() * live,
                            cap = StrokeCap.Round,
                        )
                    }
                    // Пропетанная часть — сплошная заливка акцентом
                    drawLine(active, start, progress, activeStroke, StrokeCap.Round)
                }
            }
        },
    )
}

/** Круглый бегунок: пружинный рост и ореол при захвате, как в Phase B. */
@Composable
private fun SliderThumb(
    interactionSource: MutableInteractionSource,
    thumbColor: Color,
    haloColor: Color,
) {
    val dragged by interactionSource.collectIsDraggedAsState()
    val bounce by animateFloatAsState(
        targetValue = if (dragged) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
        label = "sliderThumbBounce",
    )
    Canvas(Modifier.size(THUMB_SIZE)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 5.5.dp.toPx() * (1f + 0.30f * bounce)
        if (bounce > 0.02f) {
            drawCircle(
                color = haloColor.copy(alpha = (0.20f * bounce).coerceIn(0f, 1f)),
                radius = radius * 1.8f,
                center = center,
            )
        }
        drawCircle(thumbColor, radius = radius, center = center)
    }
}
