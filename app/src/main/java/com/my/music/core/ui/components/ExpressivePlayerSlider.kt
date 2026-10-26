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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Полный оборот фазы волны (радианы). */
private const val TWO_PI = 6.283185307179586

/**
 * Высота холста дорожки. Волна с амплитудой 9dp и обводкой 3dp занимает ~21dp,
 * плюс запас под круглый бегунок, который едет по гребню.
 */
private val TRACK_HEIGHT = 38.dp

/**
 * Ширина прозрачного thumb-слота Material3: задаёт отступ дорожки от краёв
 * и минимальную зону касания. Видимый бегунок рисуется в слоте `track`.
 */
private val THUMB_SIZE = 20.dp

/** Finish идёт через тот же FIFO, что Stop/Cancel: без гонки отдельных coroutine. */
private object SliderFinishInteraction : Interaction

/**
 * Таймлайн плеера: штатный Material3 [Slider] отвечает за жесты, drag-cancel,
 * клавиатуру, semantics и RTL, а отрисовка живёт в слоте `track`.
 *
 * Визуал — волна Phase B: плавная синусоида с длиной 44dp и сужением к краям.
 * Непропетанная часть — серая волна, буфер чуть плотнее, пропетанная — волна
 * в акценте. Круглый бегунок едет по гребню этой же волны. Ударная энергия
 * (spectral flux по трём полосам) резко поднимает амплитуду и быстро отпускает
 * её, форма синусоиды не меняется.
 * На паузе волна за 500 мс выпрямляется, фаза замирает на текущем значении.
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
    audioEnergy: State<Float>,
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

    // Пауза: волна плавно выпрямляется, как в Phase B
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "waveAmplitude",
    )

    // Бегущая фаза синусоиды: на паузе замирает на текущем значении
    val wavePhase = rememberPlaybackPhase(active = isPlaying, periodMs = 1600)

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
        // а рисуется поверх волны в её же координатах.
        thumb = { Spacer(Modifier.size(THUMB_SIZE)) },
        track = { state ->
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT),
            ) {
                // Энергия и амплитуда читаются в фазе отрисовки — без рекомпозиций.
                val fraction = state.value.coerceIn(0f, 1f)
                val buffer = bufferedFraction.coerceIn(0f, 1f)
                val live = audioEnergy.value.coerceIn(0f, 1f)
                val waveHeight = amplitude.coerceIn(0f, 1f)

                val span = size.width
                val spanD = span.toDouble()
                val centerY = size.height / 2f
                val startX = trackX(0f, span, rtl)
                val progressX = trackX(fraction, span, rtl)
                val bufferX = trackX(buffer, span, rtl)

                // Транзиентный spectral flux: корневая кривая поднимает тихие хэты,
                // а быстрый спад энергии даёт пульсацию под удар.
                val breath = sqrt(live)
                val amp = 9.dp.toPx() * waveHeight * (0.55f + 1.30f * breath).coerceIn(0f, 1.65f)
                val phase = wavePhase.value.toDouble()
                val wavelength = 44.dp.toPx().toDouble()
                val stroke = 3.dp.toPx()

                val inactive = if (enabled) trackColor else trackColor.copy(alpha = 0.12f)
                val active = if (enabled) activeColor else activeColor.copy(alpha = 0.38f)

                // Синусоида с сужением к краям — волна живёт в центре дорожки
                val wave = Path()
                wave.moveTo(startX, centerY)
                val steps = 96
                for (i in 1..steps) {
                    val u = i / steps.toDouble()
                    val x = trackX(u.toFloat(), span, rtl)
                    val taper = sin(PI * u)
                    val angle = TWO_PI * (spanD * u / wavelength) + phase
                    wave.lineTo(x, centerY - (amp * taper * sin(angle)).toFloat())
                }

                // Бегунок едет по гребню волны: та же формула в его X-координате
                val thumbU = fraction.toDouble()
                val thumbAngle = TWO_PI * (spanD * thumbU / wavelength) + phase
                val thumbY = centerY - (amp * sin(PI * thumbU) * sin(thumbAngle)).toFloat()

                // Тусклая волна — вся длительность трека
                drawPath(wave, inactive, style = Stroke(width = stroke, cap = StrokeCap.Round))

                // Зона буферизации — та же волна, чуть плотнее
                if (buffer > 0.01f) {
                    clipRect(
                        left = min(startX, bufferX),
                        right = max(startX, bufferX),
                    ) {
                        drawPath(
                            wave,
                            inactive.copy(alpha = (inactive.alpha * 1.8f).coerceAtMost(1f)),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }

                // Пропетанная часть — волна в акценте
                if (fraction > 0f) {
                    clipRect(
                        left = min(startX, progressX),
                        right = max(startX, progressX),
                    ) {
                        drawPath(wave, active, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                }

                // Ореол под бегунком — раскрывается вместе с пружиной
                if (bounce > 0.02f) {
                    drawCircle(
                        color = active.copy(alpha = (0.22f * bounce).coerceIn(0f, 1f)),
                        radius = (10f + 16f * bounce).dp.toPx(),
                        center = Offset(progressX, thumbY),
                    )
                }

                // Круглый бегунок на гребне волны
                drawCircle(
                    color = thumbColor,
                    radius = 5.dp.toPx() * (1f + 0.4f * bounce.coerceIn(-0.3f, 1.4f)),
                    center = Offset(progressX, thumbY),
                )
            }
        },
    )
}
