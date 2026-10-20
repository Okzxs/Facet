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
    val error: String? = null,
)

/**
 * ViewModel «Моей волны»: генерирует очередь и запускает её в общем плеере.
 */
@HiltViewModel
class WaveViewModel @Inject constructor(
    private val waveRepository: WaveRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaveUiState())
    val uiState: StateFlow<WaveUiState> = _uiState.asStateFlow()

    /**
     * Собрать новую волну и сразу начать воспроизведение.
     */
    fun generateWave() {
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }

            waveRepository.generateWave()
                .onSuccess { selection ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            tracks = selection.tracks,
                            fromLikes = selection.fromLikes,
                            fromHistory = selection.fromHistory,
                            fromGenres = selection.fromGenres,
                            error = if (selection.tracks.isEmpty()) EMPTY_WAVE_MESSAGE else null,
                        )
                    }
                    if (selection.tracks.isNotEmpty()) {
                        playerController.setQueueAndPlay(selection.tracks, startIndex = 0)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message ?: "Не удалось собрать волну",
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

    private companion object {
        const val EMPTY_WAVE_MESSAGE =
            "Пока не из чего собрать волну. Добавьте лайки или послушайте несколько треков."
    }
}
