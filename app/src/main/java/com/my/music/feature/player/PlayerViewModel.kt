package com.my.music.feature.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.player.PlayerController
import com.my.music.core.player.PlayerState
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.LyricsResult
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.LyricsRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана плеера.
 * Управляет воспроизведением, очередью, текстами песен и избранным.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val lyricsRepository: LyricsRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val tag = "PlayerViewModel"

    // Доступные скорости воспроизведения (по референсу MD3 плеера)
    val availableSpeeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f)

    // ─── UI-состояние ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Состояние плеера (isPlaying, currentTrack, playbackSpeed, queue, shuffle, repeat) */
    val playerState: StateFlow<PlayerState> = playerController.state

    /** Наблюдение за состоянием лайка текущего трека из БД Room */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isLiked: StateFlow<Boolean> = playerController.state
        .map { it.currentTrack?.id ?: _uiState.value.track?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId != null) {
                likedTracksRepository.isLiked(trackId)
            } else {
                flowOf(false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    init {
        // При смене текущего трека в PlayerController (автопереход/очередь) — подгружаем текст
        viewModelScope.launch {
            playerController.state
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    if (track != null) {
                        _uiState.update {
                            it.copy(
                                track = track,
                                lyrics = null,
                                isLyricsLoading = true,
                                lyricsError = null,
                            )
                        }
                        loadLyrics(track)
                    }
                }
        }
    }

    // ─── Публичные методы ─────────────────────────────────────────────────────

    /**
     * Переключить статус избранного (лайк) для текущего трека.
     */
    fun toggleLike() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        viewModelScope.launch {
            likedTracksRepository.toggleLike(track)
        }
    }

    /**
     * Запустить трек в контексте списка-источника (очереди).
     */
    fun playTrackWithQueue(track: Track, queue: List<Track>) {
        val targetQueue = if (queue.isNotEmpty()) queue else listOf(track)
        val startIndex = targetQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                track = track,
                isResolving = true,
                error = null,
                lyrics = null,
                isLyricsLoading = true,
                lyricsError = null,
            )
        }

        viewModelScope.launch {
            loadLyrics(track)
        }

        playerController.setQueueAndPlay(targetQueue, startIndex)
    }

    /**
     * Загрузить трек как одиночный элемент (обратная совместимость).
     */
    fun loadAndPlay(track: Track) {
        playTrackWithQueue(track, listOf(track))
    }

    fun playNext() {
        playerController.playNext()
    }

    fun playPrevious() {
        playerController.playPrevious()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeat() {
        playerController.toggleRepeat()
    }

    fun playQueueTrack(index: Int) {
        playerController.playTrackAt(index)
    }

    fun removeQueueTrack(index: Int) {
        playerController.removeTrackAt(index)
    }

    fun loadLyricsIfNeeded(track: Track? = null) {
        val currentTrack = track ?: _uiState.value.track ?: playerState.value.currentTrack ?: return
        if (_uiState.value.lyrics == null && !_uiState.value.isLyricsLoading) {
            _uiState.update { it.copy(isLyricsLoading = true, lyricsError = null) }
            viewModelScope.launch {
                loadLyrics(currentTrack)
            }
        }
    }

    private suspend fun loadLyrics(track: Track) {
        Log.d("LRCLIB", "PlayerViewModel.loadLyrics для трека '${track.title}' (${track.artist})")
        lyricsRepository.getLyrics(title = track.title, artist = track.artist, durationMs = track.durationMs)
            .onSuccess { result ->
                Log.d("LRCLIB", "PlayerViewModel: тексты успешно получены (isSynced=${result.isSynced}, строк=${result.lines.size})")
                _uiState.update { it.copy(lyrics = result, isLyricsLoading = false, lyricsError = null) }
            }
            .onFailure { error ->
                Log.w("LRCLIB", "PlayerViewModel: не удалось загрузить тексты: ${error.message}")
                _uiState.update { it.copy(lyrics = null, isLyricsLoading = false, lyricsError = error.message) }
            }
    }

    fun togglePlayPause() {
        playerController.pauseOrResume()
    }

    fun seekTo(fraction: Float) {
        val duration = playerController.durationMs()
        if (duration > 0) {
            playerController.seekTo((fraction * duration).toLong())
        }
    }

    fun seekToMs(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun cyclePlaybackSpeed() {
        val currentSpeed = playerState.value.playbackSpeed
        val currentIndex = availableSpeeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % availableSpeeds.size else 0
        val nextSpeed = availableSpeeds[nextIndex]
        playerController.setPlaybackSpeed(nextSpeed)
    }

    /** Текущая позиция [0..1] для слайдера. */
    fun currentFraction(): Float {
        val duration = playerController.durationMs()
        return if (duration > 0) playerController.currentPositionMs().toFloat() / duration else 0f
    }

    fun currentPositionMs(): Long = playerController.currentPositionMs()
    fun durationMs(): Long = playerController.durationMs()
}

/**
 * UI-состояние экрана плеера (ошибки, загрузка URL, тексты).
 */
data class PlayerUiState(
    val track: Track? = null,
    val isResolving: Boolean = false,
    val error: String? = null,
    val lyrics: LyricsResult? = null,
    val isLyricsLoading: Boolean = false,
    val lyricsError: String? = null,
)
