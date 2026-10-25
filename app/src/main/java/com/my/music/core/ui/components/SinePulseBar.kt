package com.my.music.core.ui.components

import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.my.music.core.player.PlaybackAudioSession
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = 6.283185307179586
private const val TAG = "SinePulse"

/** Откуда берётся энергия для пульсации. */
private enum class EnergySource { PENDING, VISUALIZER, AMBIENT }

/**
 * Тонкая синусоида над слайдером: дышит в такт басу.
 *
 * Энергия берётся из [Visualizer] на аудиосессии ExoPlayer (FFT → средняя
 * амплитуда низких бинов → плавный lerp). Если Visualizer не поднялся
 * (нет разрешения, кастомный ROM, сессия ещё не создана) — мягкий ambient
 * по `sin(t)`, чтобы линия не замирала. Хитбокс слайдера не затрагивается:
 * полоса декоративная и не перехватывает касания.
 */
@Composable
fun SinePulseBar(
    isPlaying: Boolean,
    trackId: String?,
    canVisualize: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sampler = remember { BassSampler() }
    var source by remember { mutableStateOf(EnergySource.PENDING) }

    DisposableEffect(Unit) {
        onDispose { sampler.release() }
    }

    // Подключаемся к аудиосессии: она появляется не мгновенно после старта трека,
    // поэтому даём несколько коротких попыток, прежде чем уйти в ambient.
    LaunchedEffect(isPlaying, trackId, canVisualize) {
        if (!isPlaying) return@LaunchedEffect
        if (sampler.isActive) {
            source = EnergySource.VISUALIZER
            return@LaunchedEffect
        }
        if (!canVisualize) {
            // Разрешение ещё не выдано (или отклонено) — сразу мягкий ambient,
            // чтобы линия не стояла мёртвой. При выдаче эффект перезапустится.
            source = EnergySource.AMBIENT
            Log.d(TAG, "Нет RECORD_AUDIO — ambient-фолбэк")
            return@LaunchedEffect
        }
        source = EnergySource.PENDING
        repeat(12) {
            if (sampler.start()) {
                source = EnergySource.VISUALIZER
                return@LaunchedEffect
            }
            delay(250)
        }
        source = EnergySource.AMBIENT
        Log.d(TAG, "Visualizer недоступен — ambient-фолбэк")
    }

    // Ambient-фолбэк: медленное дыхание без случайного дрожания.
    LaunchedEffect(isPlaying, source) {
        if (!isPlaying || source != EnergySource.AMBIENT) return@LaunchedEffect
        var t = 0f
        while (true) {
            val wave = 0.42f + 0.26f * sin(t * 1.15f) + 0.10f * sin(t * 0.53f + 1.2f)
            sampler.pushNormalized(wave)
            t += 0.08f
            delay(80)
        }
    }

    // Смена трека: короткое затухание и мягкое возвращение.
    var gateTarget by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(trackId) {
        gateTarget = 0f
        delay(120)
        gateTarget = 1f
    }
    val gate by animateFloatAsState(
        targetValue = gateTarget,
        animationSpec = tween(durationMillis = 360),
        label = "sineGate",
    )

    val energy = sampler.energy.floatValue
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) (0.15f + 0.85f * energy) * gate else 0f,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "sineAmplitude",
    )
    val phase = rememberPlaybackPhase(active = isPlaying, periodMs = 2600)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val centerY = size.height / 2f
        val amp = (size.height / 2f - 1.5.dp.toPx()) * amplitude
        val strokeWidth = 1.5.dp.toPx()
        val wave = Path()
        val steps = 72
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val x = size.width * u
            // Края плавно сходятся к линии — без обрубленных концов
            val taper = sin(PI * u).toFloat()
            val angle = (TWO_PI * 3.0 * u - phase.value).toFloat()
            val y = centerY + amp * taper * sin(angle)
            if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        drawPath(
            path = wave,
            color = color.copy(alpha = 0.5f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/**
 * Сэмплер баса: держит [Visualizer], считает энергию низких бинов и сглаживает её.
 * Вся математика — без аллокаций в колбэке (буфер переиспользуется).
 */
private class BassSampler {

    val energy = mutableFloatStateOf(0f)

    private var visualizer: Visualizer? = null
    private var smoothed = 0f

    val isActive: Boolean get() = visualizer != null

    /** true — визуализатор поднялся и уже отдаёт данные. */
    fun start(): Boolean {
        if (visualizer != null) return true

        val sessionId = PlaybackAudioSession.audioSessionId
        if (sessionId == 0) return false

        val created = runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange().last().coerceAtMost(1024)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (fft == null) return
                            // Низкие бины: ~0..500 Гц при FFT 1024. Формат — real/imag по байту.
                            var sum = 0f
                            var count = 0
                            var i = 2
                            while (i + 1 < fft.size && i < 24) {
                                val re = fft[i].toFloat()
                                val im = fft[i + 1].toFloat()
                                sum += sqrt(re * re + im * im)
                                count++
                                i += 2
                            }
                            if (count == 0) return
                            pushNormalized(sum / count / 96f)
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    false,
                    true,
                )
                enabled = true
            }
        }.getOrNull()

        if (created == null) return false
        visualizer = created
        Log.d(TAG, "Visualizer активен, сессия $sessionId")
        return true
    }

    fun pushNormalized(value: Float) {
        // Мягкая кривая: быстрая атака на удар, медленное затухание — без дрожания.
        val target = value.coerceIn(0f, 1f)
        val rate = if (target > smoothed) 0.35f else 0.10f
        smoothed += (target - smoothed) * rate
        if (kotlin.math.abs(smoothed - energy.floatValue) > 0.004f) {
            energy.floatValue = smoothed
        }
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        smoothed = 0f
        energy.floatValue = 0f
    }
}
