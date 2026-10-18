package com.my.music.core.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Track
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton-контроллер над ExoPlayer с полноценным управлением очередью,
 * перемешиванием (shuffle), повтором (repeat) и автовоспроизведением.
 */
@Singleton
class PlayerController @Inject constructor(
    private val exoPlayer: ExoPlayer,
    private val soundCloudRepository: SoundCloudRepository,
    private val playHistoryRepository: PlayHistoryRepository,
) {

    private val tag = "PlayerController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null

    // Исходная очередь до перемешивания (для восстановления исходного порядка)
    private var originalQueue: List<Track> = emptyList()

    // ─── Публичное состояние ──────────────────────────────────────────────────

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // ─── Слушатель событий ExoPlayer ─────────────────────────────────────────

    init {
        exoPlayer.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _state.update {
                    it.copy(
                        isLoading = isBuffering,
                        isBuffering = isBuffering,
                        playbackState = playbackState,
                        error = if (playbackState == Player.STATE_READY) null else it.error,
                    )
                }

                if (playbackState == Player.STATE_ENDED) {
                    onTrackEnded()
                }
            }
        })
    }

    private fun onTrackEnded() {
        val currentState = _state.value
        when (currentState.repeatMode) {
            RepeatMode.ONE -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            RepeatMode.ALL -> {
                playNext(loop = true)
            }
            RepeatMode.OFF -> {
                if (hasNext()) {
                    playNext(loop = false)
                } else {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    // ─── Управление очередью ─────────────────────────────────────────────────

    /**
     * Задать новую очередь треков и начать воспроизведение с [startIndex].
     */
    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val clampedIndex = startIndex.coerceIn(0, tracks.lastIndex)
        originalQueue = tracks

        val effectiveQueue = if (_state.value.isShuffle) {
            val selected = tracks[clampedIndex]
            val rest = tracks.filterIndexed { index, _ -> index != clampedIndex }.shuffled()
            listOf(selected) + rest
        } else {
            tracks
        }

        val effectiveIndex = if (_state.value.isShuffle) 0 else clampedIndex

        _state.update {
            it.copy(
                queue = effectiveQueue,
                currentIndex = effectiveIndex,
            )
        }

        playTrackAt(effectiveIndex)
    }

    /**
     * Воспроизвести трек по индексу в текущей очереди.
     */
    fun playTrackAt(index: Int) {
        val tracks = _state.value.queue
        if (index !in tracks.indices) return

        val track = tracks[index]
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.seekTo(0)
        _state.update {
            it.copy(
                currentIndex = index,
                currentTrack = track,
                isPlaying = false,
                isLoading = true,
                isBuffering = false,
                error = null,
            )
        }

        resolveAndPlay(track)
    }

    /**
     * Переход к следующему треку.
     * [loop] — зацикливать ли в начало при достижении конца очереди.
     */
    fun playNext(loop: Boolean = true) {
        val queue = _state.value.queue
        if (queue.isEmpty()) return

        val currentIndex = _state.value.currentIndex
        val nextIndex = currentIndex + 1

        if (nextIndex in queue.indices) {
            playTrackAt(nextIndex)
        } else if (loop || _state.value.repeatMode == RepeatMode.ALL) {
            playTrackAt(0)
        }
    }

    /**
     * Переход к предыдущему треку.
     * Если прошло больше 3 секунд от начала — перезапускает текущий трек.
     */
    fun playPrevious() {
        val currentPos = exoPlayer.currentPosition
        if (currentPos > 3000L) {
            exoPlayer.seekTo(0)
            return
        }

        val queue = _state.value.queue
        if (queue.isEmpty()) return

        val currentIndex = _state.value.currentIndex
        val prevIndex = currentIndex - 1

        if (prevIndex >= 0) {
            playTrackAt(prevIndex)
        } else if (_state.value.repeatMode == RepeatMode.ALL) {
            playTrackAt(queue.lastIndex)
        } else {
            exoPlayer.seekTo(0)
        }
    }

    fun hasNext(): Boolean {
        val queue = _state.value.queue
        if (queue.isEmpty()) return false
        val nextIndex = _state.value.currentIndex + 1
        return nextIndex in queue.indices || _state.value.repeatMode == RepeatMode.ALL
    }

    fun hasPrevious(): Boolean {
        val queue = _state.value.queue
        if (queue.isEmpty()) return false
        val prevIndex = _state.value.currentIndex - 1
        return prevIndex >= 0 || _state.value.repeatMode == RepeatMode.ALL
    }

    /**
     * Переключение режима перемешивания (shuffle) с сохранением исходного списка.
     */
    fun toggleShuffle() {
        val currentQueue = _state.value.queue
        val currentTrack = _state.value.currentTrack
        val newShuffle = !_state.value.isShuffle

        if (newShuffle) {
            // Включаем shuffle: сохраняем originalQueue, текущий трек остаётся во главе
            if (originalQueue.isEmpty() || originalQueue.size != currentQueue.size) {
                originalQueue = currentQueue
            }
            val others = currentQueue.filter { it.id != currentTrack?.id }.shuffled()
            val shuffled = if (currentTrack != null) listOf(currentTrack) + others else others

            _state.update {
                it.copy(
                    isShuffle = true,
                    queue = shuffled,
                    currentIndex = 0,
                )
            }
        } else {
            // Выключаем shuffle: восстанавливаем originalQueue
            val restoredQueue = if (originalQueue.isNotEmpty()) originalQueue else currentQueue
            val newIndex = restoredQueue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)

            _state.update {
                it.copy(
                    isShuffle = false,
                    queue = restoredQueue,
                    currentIndex = newIndex,
                )
            }
        }
    }

    /**
     * Переключение режима повтора: OFF -> ALL -> ONE -> OFF.
     */
    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeatMode = nextMode) }
    }

    /**
     * Удаление трека из очереди по индексу.
     */
    fun removeTrackAt(index: Int) {
        val currentQueue = _state.value.queue.toMutableList()
        if (index !in currentQueue.indices) return

        val removedTrack = currentQueue.removeAt(index)
        originalQueue = originalQueue.filter { it.id != removedTrack.id }

        val currentIndex = _state.value.currentIndex
        when {
            currentQueue.isEmpty() -> {
                exoPlayer.stop()
                _state.update {
                    it.copy(
                        queue = emptyList(),
                        currentIndex = -1,
                        currentTrack = null,
                        isPlaying = false,
                    )
                }
            }
            index < currentIndex -> {
                _state.update {
                    it.copy(
                        queue = currentQueue,
                        currentIndex = currentIndex - 1,
                    )
                }
            }
            index == currentIndex -> {
                // Если удалили играющий трек: переходим к следующему
                val newIndex = if (index < currentQueue.size) index else 0
                _state.update {
                    it.copy(
                        queue = currentQueue,
                        currentIndex = newIndex,
                    )
                }
                playTrackAt(newIndex)
            }
            else -> {
                _state.update { it.copy(queue = currentQueue) }
            }
        }
    }

    // ─── Воспроизведение потока ───────────────────────────────────────────────

    /**
     * Разрешить URL трека и передать в ExoPlayer.
     */
    private fun resolveAndPlay(track: Track) {
        resolveJob?.cancel()
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.seekTo(0)
        resolveJob = scope.launch {
            // Запись в историю воспроизведения Room
            playHistoryRepository.addToHistory(track)

            val streamResult: Result<AudioStreamResult> = when {
                track.streamUrl != null ->
                    Result.success(AudioStreamResult(url = track.streamUrl, isHls = false))
                track.transcodingUrl != null ->
                    soundCloudRepository.resolveStreamUrl(track.transcodingUrl)
                else ->
                    Result.failure(IllegalStateException("Нет ссылки на поток для трека: ${track.id}"))
            }

            streamResult
                .onSuccess { result ->
                    Log.d(tag, "Воспроизведение трека '${track.title}': isHls=${result.isHls}")
                    playStream(track, result)
                }
                .onFailure { error ->
                    Log.e(tag, "Ошибка resolve для трека '${track.title}'", error)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Ошибка воспроизведения",
                        )
                    }
                }
        }
    }

    /**
     * Прямой запуск потока (для вызова с уже готовым AudioStreamResult).
     */
    fun play(track: Track, streamResult: AudioStreamResult) {
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.seekTo(0)
        // Если очередь была пуста — создаём одиночную очередь
        if (_state.value.queue.none { it.id == track.id }) {
            originalQueue = listOf(track)
            _state.update {
                it.copy(
                    queue = listOf(track),
                    currentIndex = 0,
                )
            }
        }
        playStream(track, streamResult)
    }

    private fun playStream(track: Track, streamResult: AudioStreamResult) {
        val mimeType = if (streamResult.isHls) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.AUDIO_MPEG
        }

        val mediaItem = MediaItem.Builder()
            .setUri(streamResult.url)
            .setMimeType(mimeType)
            .build()

        exoPlayer.apply {
            setMediaItem(mediaItem)
            seekTo(0)
            prepare()
            play()
        }

        _state.update {
            it.copy(
                currentTrack = track,
                isPlaying = true,
                isLoading = false,
                isBuffering = false,
                error = null,
            )
        }
    }

    fun pauseOrResume() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun currentPositionMs(): Long = exoPlayer.currentPosition

    fun durationMs(): Long = exoPlayer.duration.coerceAtLeast(0L)

    fun bufferedPositionMs(): Long = exoPlayer.bufferedPosition.coerceAtLeast(0L)

    fun bufferedFraction(): Float {
        val duration = durationMs()
        return if (duration > 0) (bufferedPositionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    fun release() {
        resolveJob?.cancel()
        exoPlayer.release()
    }
}

/**
 * Снимок состояния плеера и очереди для UI.
 */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackState: Int = androidx.media3.common.Player.STATE_IDLE,
    val playbackSpeed: Float = 1.0f,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val error: String? = null,
)
