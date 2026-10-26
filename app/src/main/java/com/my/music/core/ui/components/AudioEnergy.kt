package com.my.music.core.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.my.music.core.player.PlaybackAudioSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Тег для проверки на устройстве: сработал Visualizer или ambient-фолбэк. */
private const val TAG = "AudioEnergy"

/**
 * Ударная энергия для декора таймлайна: амплитуда волны.
 * Значение читается только в фазе отрисовки; ambient декоративный, без beat sync.
 *
 * @param delayMs калибровка задержки Visualizer: волна читает энергию,
 * которая была [delayMs] назад, чтобы удар совпал со звуком.
 */
@Composable
internal fun rememberAudioEnergy(
    isPlaying: Boolean,
    trackId: String?,
    canVisualize: Boolean,
    delayMs: Int,
): State<Float> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember { AudioSampler() }
    val envelope = remember { AudioEnvelope() }
    val energy = remember { mutableFloatStateOf(0f) }
    // Ползунок двигается во время воспроизведения: не перезапускаем кадровый цикл.
    val latestDelayMs = rememberUpdatedState(delayMs)
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(hasPermission()) }
    var sessionId by remember { mutableIntStateOf(PlaybackAudioSession.audioSessionId) }

    DisposableEffect(lifecycle, sampler) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) permission = hasPermission()
            if (!resumed) sampler.release()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sampler.release()
        }
    }
    // Provider не публикует Flow: опрашиваем доступный id, не меняя движок.
    LaunchedEffect(resumed, isPlaying, canVisualize) {
        while (isActive && resumed) {
            permission = hasPermission()
            sessionId = PlaybackAudioSession.audioSessionId
            delay(250)
        }
    }
    DisposableEffect(isPlaying, resumed, permission, sessionId, trackId) {
        sampler.release()
        if (isPlaying && resumed && permission && sessionId > 0) {
            sampler.start(sessionId)
            Log.d(TAG, if (sampler.isActive) "Visualizer: сессия $sessionId" else "Visualizer недоступен — ambient")
        } else if (isPlaying && resumed) {
            Log.d(TAG, "Нет RECORD_AUDIO или аудиосессии — ambient")
        }
        onDispose { sampler.release() }
    }
    LaunchedEffect(isPlaying, resumed) {
        while (isActive) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtimeNanos()
                val target = when {
                    !isPlaying || !resumed -> 0f
                    sampler.isActive -> sampler.energyAt(now, latestDelayMs.value)
                    else -> 0.20f + 0.08f * sin(now / 1_000_000_000.0 * 1.15).toFloat()
                }
                energy.floatValue = envelope.update(target, now)
            }
            if (!resumed && energy.floatValue < 0.001f) break
        }
    }
    return energy
}

/** Callback не пишет Compose-state; поколение исключает поздние FFT после release. */
private class AudioSampler {
    @Volatile var sample = AudioSample()
        private set
    @Volatile private var generation = 0L
    private val detector = TransientDetector()
    private val energyHistory = EnergyDelayBuffer()
    private var visualizer: Visualizer? = null
    val isActive: Boolean get() = visualizer != null

    /** Энергия с калибровочной задержкой; 0, если FFT давно не приходил. */
    @Synchronized
    fun energyAt(nowNanos: Long, delayMs: Int): Float {
        val newest = sample
        if (newest.capturedNanos == Long.MIN_VALUE ||
            nowNanos < newest.capturedNanos ||
            nowNanos - newest.capturedNanos > AudioSample.FRESH_NANOS
        ) return 0f
        return energyHistory.valueAt(nowNanos, delayMs)
    }

    fun start(sessionId: Int) {
        val token = ++generation
        detector.reset()
        synchronized(this) { energyHistory.reset() }
        var created: Visualizer? = null
        try {
            val instance = Visualizer(sessionId)
            created = instance
            instance.captureSize = Visualizer.getCaptureSizeRange().last().coerceAtMost(1024)
            check(instance.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null) return
                    synchronized(this@AudioSampler) {
                        if (generation != token) return
                        val value = detector.process(fft, samplingRate)
                        val capturedNanos = SystemClock.elapsedRealtimeNanos()
                        sample = AudioSample(value, capturedNanos)
                        energyHistory.push(value, capturedNanos)
                    }
                }
            }, Visualizer.getMaxCaptureRate().coerceAtMost(20_000), false, true) == Visualizer.SUCCESS)
            instance.enabled = true
            visualizer = instance
        } catch (_: RuntimeException) {
            synchronized(this) {
                generation++
                sample = AudioSample()
                detector.reset()
                energyHistory.reset()
            }
            runCatching { created?.enabled = false }
            runCatching { created?.release() }
        }
    }

    fun release() {
        synchronized(this) {
            generation++
            sample = AudioSample()
            detector.reset()
            energyHistory.reset()
        }
        val old = visualizer
        visualizer = null
        runCatching { old?.enabled = false }
        runCatching { old?.release() }
    }
}
