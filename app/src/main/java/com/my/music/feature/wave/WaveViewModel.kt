package com.my.music.feature.wave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.player.PlayerController
import com.my.music.data.model.Track
import com.my.music.data.repository.WaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaveUiState(
    val isGenerating: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val fromLikes: Int = 0,
    val fromHistory: Int = 0,
    val fromGenres: Int = 0,
    val fromSimilar: Int = 0,
    val genres: List<String> = emptyList(),
    val isRussianPriority: Boolean = true,
    val isExhausted: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel «Моей волны»: запускает сессию и синхронизирует очередь плеера.
 */
@HiltViewModel
class WaveViewModel @Inject constructor(
    private val waveRepository: WaveRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaveUiState())
    val uiState: StateFlow<WaveUiState> = _uiState.asStateFlow()

    init {
        // Счётчики и жанры берём из накопленной сессии — они обновляются
        // и при автодозагрузке следующих пачек.
        viewModelScope.launch {
            waveRepository.activeSession.collect { session ->
                if (session == null) {
                    _uiState.update {
                        it.copy(
                            tracks = emptyList(),
                            fromLikes = 0,
                            fromHistory = 0,
                            fromGenres = 0,
                            fromSimilar = 0,
                            genres = emptyList(),
                            isExhausted = false,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            fromLikes = session.fromLikes,
                            fromHistory = session.fromHistory,
                            fromGenres = session.fromGenres,
                            fromSimilar = session.fromSimilar,
                            genres = session.seedGenres,
                            isRussianPriority = session.isRussianPriority,
                            isExhausted = session.isTerminated,
                        )
                    }
                }
            }
        }

        // Автодозагрузка меняет очередь напрямую в PlayerController —
        // отражаем её в списке волны.
        viewModelScope.launch {
            playerController.state.collect { state ->
                if (waveRepository.activeSession.value == null) return@collect
                if (_uiState.value.isGenerating) return@collect
                if (state.queue.isEmpty()) return@collect
                _uiState.update { it.copy(tracks = state.queue) }
            }
        }
    }

    /**
     * Собрать новую сессию волны и сразу начать воспроизведение.
     */
    fun generateWave() {
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    tracks = emptyList(),
                    fromLikes = 0,
                    fromHistory = 0,
                    fromGenres = 0,
                    fromSimilar = 0,
                    genres = emptyList(),
                    isExhausted = false,
                    error = null,
                )
            }

            waveRepository.startSession()
                .onSuccess {
                    waveRepository.nextBatch()
                        .onSuccess { batch ->
                            if (batch.tracks.isEmpty()) {
                                waveRepository.reset()
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        error = EMPTY_WAVE_MESSAGE,
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isGenerating = false,
                                        tracks = batch.tracks,
                                        fromLikes = batch.session.fromLikes,
                                        fromHistory = batch.session.fromHistory,
                                        fromGenres = batch.session.fromGenres,
                                        fromSimilar = batch.session.fromSimilar,
                                        genres = batch.session.seedGenres,
                                        isRussianPriority = batch.session.isRussianPriority,
                                        isExhausted = false,
                                        error = null,
                                    )
                                }
                                playerController.setQueueAndPlay(batch.tracks, startIndex = 0)
                            }
                        }
                        .onFailure { error ->
                            waveRepository.reset()
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    error = error.message ?: GENERATE_ERROR,
                                )
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message ?: GENERATE_ERROR,
                        )
                    }
                }
        }
    }

    /**
     * Воспроизвести выбранный трек в контексте текущей волны.
     */
    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index < 0) return
        playerController.setQueueAndPlay(tracks, startIndex = index)
    }

    /**
     * Завершить сессию волны. Текущая музыка продолжает играть,
     * но автодозагрузка новых пачек останавливается.
     */
    fun resetWave() {
        viewModelScope.launch {
            waveRepository.reset()
            _uiState.update {
                it.copy(
                    tracks = emptyList(),
                    fromLikes = 0,
                    fromHistory = 0,
                    fromGenres = 0,
                    fromSimilar = 0,
                    genres = emptyList(),
                    isExhausted = false,
                    error = null,
                )
            }
        }
    }

    private companion object {
        const val EMPTY_WAVE_MESSAGE =
            "Волна пока пуста. Добавьте лайки, послушайте треки или запустите её позже."
        const val GENERATE_ERROR = "Не удалось собрать волну"
    }
}
