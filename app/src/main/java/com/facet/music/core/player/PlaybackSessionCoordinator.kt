package com.facet.music.core.player

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.facet.music.core.analytics.AnalyticsEvents
import com.facet.music.core.analytics.AnalyticsTracker
import com.facet.music.core.di.ApplicationScope
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.TasteContext
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фоновая логика сессии, которая не должна умирать вместе с Activity:
 * история, вкусы, аналитика, refill волны и периодическое сохранение позиции.
 */
@Singleton
class PlaybackSessionCoordinator @Inject constructor(
    private val playerController: PlayerController,
    private val queueRepository: QueueRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val tasteRepository: TasteRepository,
    private val waveRepository: WaveRepository,
    private val analyticsTracker: AnalyticsTracker,
    @ApplicationScope private val scope: CoroutineScope,
) {

    @Volatile
    private var started = false

    private var tickerJob: Job? = null
    private var waveRefillJob: Job? = null

    private var currentTrack: Track? = null
    private var currentOrigin: QueueOrigin = QueueOrigin.MAIN
    private var playedMs = 0L
    private var lastTickAt = 0L
    private var historyWritten = false
    private var analyticsFired = false
    private var wasPlaying = false
    private var lastSavedAt = 0L

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch { playerController.resumeAfterFocusLoss() }
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        tickerJob = scope.launch { tick() }
    }

    private suspend fun tick() {
        lastTickAt = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            delay(TICK_MS)
            val state = playerController.state.value
            val now = SystemClock.elapsedRealtime()
            val playingNow = state.isPlaying
            if (playingNow && wasPlaying) {
                playedMs += (now - lastTickAt).coerceAtLeast(0L)
            }
            lastTickAt = now

            val track = state.currentTrack
            if (track?.id != currentTrack?.id) {
                val previous = currentTrack
                if (previous != null) finalizeTrack(previous, playedMs, currentOrigin)
                currentTrack = track
                currentOrigin = state.queueOrigin
                playedMs = 0L
                historyWritten = false
                analyticsFired = false
                if (track != null) {
                    playerController.preloadNext()
                    saveQueue()
                }
            }

            if (track != null && !historyWritten) {
                val fraction = if (track.durationMs > 0L) {
                    playedMs.toFloat() / track.durationMs
                } else {
                    0f
                }
                if (playedMs >= HISTORY_MIN_MS || fraction >= 0.5f) {
                    historyWritten = true
                    runCatching { playHistoryRepository.addToHistory(track) }
                }
            }

            if (track != null && playingNow && !analyticsFired) {
                analyticsFired = true
                analyticsTracker.track(AnalyticsEvents.TRACK_PLAY)
            }

            if (playingNow) {
                maybeRefillWave(state)
                if (now - lastSavedAt >= SAVE_INTERVAL_MS) saveQueue()
            } else if (wasPlaying) {
                saveQueue()
            }
            wasPlaying = playingNow
        }
    }

    private suspend fun finalizeTrack(track: Track, played: Long, origin: QueueOrigin) {
        if (played <= 0L) return
        val context = if (origin == QueueOrigin.MAIN) {
            TasteContext.NORMAL
        } else {
            TasteContext.WAVE
        }
        val fraction = if (track.durationMs > 0L) {
            (played.toFloat() / track.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        runCatching {
            when {
                fraction > FINISHED_THRESHOLD -> tasteRepository.onTrackFinished(track, fraction, context)
                played < SKIP_THRESHOLD_MS -> tasteRepository.onSkip(track, played, context)
            }
        }
    }

    private fun maybeRefillWave(state: PlayerState) {
        val session = waveRepository.activeSession.value ?: return
        if (session.isTerminated) return
        if (state.currentTrack == null || state.queue.isEmpty()) return
        if (state.queue.lastIndex - state.currentIndex > WAVE_REFILL_THRESHOLD) return
        if (waveRefillJob?.isActive == true) return
        waveRefillJob = scope.launch {
            waveRepository.nextBatch(size = WAVE_REFILL_BATCH).onSuccess { batch ->
                val active = waveRepository.activeSession.value
                if (batch.tracks.isNotEmpty() &&
                    active?.sessionId == batch.session.sessionId
                ) {
                    playerController.appendToQueue(batch.tracks)
                }
            }
        }
    }

    private suspend fun saveQueue() {
        val state = playerController.state.value
        if (state.queue.isEmpty()) return
        val position = withContext(Dispatchers.Main.immediate) {
            playerController.currentPositionMs()
        }
        queueRepository.save(
            tracks = state.queue,
            currentIndex = state.currentIndex.coerceIn(0, state.queue.lastIndex),
            positionMs = position,
        )
        lastSavedAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val HISTORY_MIN_MS = 30_000L
        const val FINISHED_THRESHOLD = 0.8f
        const val SKIP_THRESHOLD_MS = 30_000L
        const val WAVE_REFILL_THRESHOLD = 3
        const val WAVE_REFILL_BATCH = 12
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
