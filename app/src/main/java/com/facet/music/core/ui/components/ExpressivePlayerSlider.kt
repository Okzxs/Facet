package com.facet.music.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Высота холста дорожки: сама линия 4dp, остальное — запас под круглый бегунок,
 * который не должен обрезаться границами Canvas.
 */
private val TRACK_HEIGHT = 24.dp

/** Толщина линии таймлайна. */
private val TRACK_THICKNESS = 4.dp

/**
 * Ширина прозрачного thumb-слота Material3: задаёт отступ дорожки от краёв
 * и минимальную зону касания. Видимый бегунок рисуется в слоте `track`.
 */
private val THUMB_SIZE = 20.dp

/** Радиус видимого бегунка. */
private val THUMB_RADIUS = 6.dp

/** Finish идёт через тот же FIFO, что Stop/Cancel: без гонки отдельных coroutine. */
private object SliderFinishInteraction : Interaction

/**
 * Таймлайн плеера: штатный Material3 [Slider] отвечает за жесты, drag-cancel,
 * клавиатуру, semantics и RTL, а отрисовка живёт в слоте `track`.
 *
 * Визуал — спокойная линия 4dp: непропетанная часть приглушена, буфер чуть
 * плотнее, пропетанная — в акценте. Бегунок 12dp раскрывает ореол при захвате
 * и пружинит, поэтому касание читается на ощупь и на глаз.
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
    modifier: Modifier = Modifier,
    bufferedFraction: Float = 0f,
    enabled: Boolean = true,
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

    // Пружина бегунка при захвате
    val dragged by interactionSource.collectIsDraggedAsState()
    val bounce by animateFloatAsState(
        targetValue = if (dragged) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
        label = "sliderThumbBounce",
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
        // Бегунок невидимый: он держит ширину дорожки и зону касания,
        // а рисуется поверх линии в её же координатах.
        thumb = { Spacer(Modifier.size(THUMB_SIZE)) },
        track = { state ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT),
            ) {
                // Позиция и буфер читаются в фазе отрисовки — без рекомпозиций.
                val fraction = state.value.coerceIn(0f, 1f)
                val buffer = bufferedFraction.coerceIn(0f, 1f)

                val span = size.width
                val centerY = size.height / 2f
                val startX = trackX(0f, span, rtl)
                val progressX = trackX(fraction, span, rtl)
                val bufferX = trackX(buffer, span, rtl)
                val thickness = TRACK_THICKNESS.toPx()

                val inactive = if (enabled) trackColor else trackColor.copy(alpha = 0.12f)
                val active = if (enabled) activeColor else activeColor.copy(alpha = 0.38f)

                // Непропетанная часть — вся длительность трека
                drawLine(
                    color = inactive,
                    start = Offset(startX, centerY),
                    end = Offset(trackX(1f, span, rtl), centerY),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )

                // Зона буферизации — та же линия, чуть плотнее
                if (buffer > 0.01f) {
                    drawLine(
                        color = inactive.copy(alpha = (inactive.alpha * 1.8f).coerceAtMost(1f)),
                        start = Offset(startX, centerY),
                        end = Offset(bufferX, centerY),
                        strokeWidth = thickness,
                        cap = StrokeCap.Round,
                    )
                }

                // Пропетанная часть — линия в акценте
                if (fraction > 0f) {
                    drawLine(
                        color = active,
                        start = Offset(startX, centerY),
                        end = Offset(progressX, centerY),
                        strokeWidth = thickness,
                        cap = StrokeCap.Round,
                    )
                }

                // Ореол под бегунком — раскрывается вместе с пружиной
                if (bounce > 0.02f) {
                    drawCircle(
                        color = active.copy(alpha = (0.22f * bounce).coerceIn(0f, 1f)),
                        radius = (10f + 14f * bounce).dp.toPx(),
                        center = Offset(progressX, centerY),
                    )
                }

                // Круглый бегунок на линии
                drawCircle(
                    color = thumbColor,
                    radius = THUMB_RADIUS.toPx() * (1f + 0.4f * bounce.coerceIn(-0.3f, 1.4f)),
                    center = Offset(progressX, centerY),
                )
                // Тонкий контур отделяет бегунок от светлой линии
                drawCircle(
                    color = active.copy(alpha = 0.35f),
                    radius = THUMB_RADIUS.toPx() + 1.dp.toPx(),
                    center = Offset(progressX, centerY),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        },
    )
}
