package com.facet.music.feature.wave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlayerController
import com.facet.music.core.player.QueueOrigin
import com.facet.music.core.wave.WaveCandidateDetails
import com.facet.music.core.wave.WaveMood
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LastFmRepository
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.data.repository.WaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class WaveUiState(
    val isGenerating: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val fromLikes: Int = 0,
    val fromHistory: Int = 0,
    val fromGenres: Int = 0,
    val fromSimilar: Int = 0,
    val genres: List<String> = emptyList(),
    val mood: WaveMood = WaveMood.BALANCE,
    val details: Map<String, WaveCandidateDetails> = emptyMap(),
    val isExhausted: Boolean = false,
    val error: String? = null,
    val isColdStartLoading: Boolean = false,
    val coldStartArtists: List<String> = emptyList(),
    val selectedColdStartArtists: Set<String> = emptySet(),
    val showColdStart: Boolean = false,
)

@HiltViewModel
class WaveViewModel @Inject constructor(
    private val waveRepository: WaveRepository,
    private val playerController: PlayerController,
    private val settingsRepository: SettingsRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val lastFmRepository: LastFmRepository,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaveUiState())
    val uiState: StateFlow<WaveUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(mood = settings.waveMood) }
            }
        }

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
                            details = emptyMap(),
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
                            isExhausted = session.isTerminated,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            playerController.state.collect { state ->
                if (waveRepository.activeSession.value == null) return@collect
                if (_uiState.value.isGenerating) return@collect
                if (state.queue.isEmpty()) return@collect
                _uiState.update { it.copy(tracks = state.queue) }
            }
        }
    }

    fun generateWave() {
        if (_uiState.value.isGenerating) return
        launchGeneration(seedArtists = emptyList())
    }

    fun refreshWave() {
        if (_uiState.value.isGenerating) return
        launchGeneration(seedArtists = emptyList())
    }

    fun setMood(mood: WaveMood) {
        if (_uiState.value.mood == mood) return
        _uiState.update { it.copy(mood = mood) }
        viewModelScope.launch {
            settingsRepository.setWaveMood(mood)
            if (!_uiState.value.isGenerating && _uiState.value.tracks.isNotEmpty()) {
                launchGeneration(seedArtists = emptyList())
            }
        }
    }

    private fun launchGeneration(seedArtists: List<String>) {
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
                    details = emptyMap(),
                    isExhausted = false,
                    error = null,
                    showColdStart = false,
                )
            }

            val start = waveRepository.startSession(
                mood = _uiState.value.mood,
                seedArtistNames = seedArtists,
            )
            start.onSuccess {
                waveRepository.nextBatch()
                    .onSuccess { batch ->
                        if (batch.tracks.isEmpty()) {
                            waveRepository.reset()
                            if (seedArtists.isEmpty()) {
                                prepareColdStart()
                            } else {
                                _uiState.update {
                                    it.copy(isGenerating = false, error = EMPTY_WAVE_MESSAGE)
                                }
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
                                    details = batch.details,
                                    isExhausted = false,
                                    error = null,
                                )
                            }
                            playerController.setQueueAndPlay(
                                batch.tracks,
                                startIndex = 0,
                                origin = QueueOrigin.WAVE,
                            )
                        }
                    }
                    .onFailure { error ->
                        waveRepository.reset()
                        if (seedArtists.isEmpty()) {
                            prepareColdStart()
                        } else {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    error = error.message ?: GENERATE_ERROR,
                                )
                            }
                        }
                    }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isGenerating = false, error = error.message ?: GENERATE_ERROR)
                }
            }
        }
    }

    private suspend fun prepareColdStart() {
        val likes = runCatching { likedTracksRepository.getLikedTracks().first().size }
            .getOrDefault(0)
        val history = runCatching { playHistoryRepository.getPlayHistory().first().size }
            .getOrDefault(0)
        if (likes >= COLD_START_LIKES && history >= COLD_START_HISTORY) {
            _uiState.update { it.copy(isGenerating = false, error = EMPTY_WAVE_MESSAGE) }
            return
        }
        _uiState.update {
            it.copy(isGenerating = false, isColdStartLoading = true, showColdStart = true)
        }
        var artists = withTimeoutOrNull(5_000) {
            lastFmRepository.getTopArtists(COLD_START_ARTIST_LIMIT).getOrDefault(emptyList())
        }.orEmpty()
        if (artists.isEmpty()) {
            artists = youTubeRepository.searchArtists("popular music", COLD_START_ARTIST_LIMIT)
                .getOrDefault(emptyList())
                .map { it.name }
                .distinct()
        }
        _uiState.update {
            it.copy(
                isColdStartLoading = false,
                coldStartArtists = artists,
                selectedColdStartArtists = it.selectedColdStartArtists
                    .intersect(artists.toSet()),
            )
        }
    }

    fun toggleColdStartArtist(artist: String) {
        _uiState.update { state ->
            val selected = state.selectedColdStartArtists.toMutableSet()
            if (!selected.add(artist)) selected.remove(artist)
            state.copy(selectedColdStartArtists = selected)
        }
    }

    fun confirmColdStart() {
        val selected = _uiState.value.selectedColdStartArtists.take(MAX_COLD_START_ARTISTS)
        if (selected.size < MIN_COLD_START_ARTISTS) return
        _uiState.update { it.copy(showColdStart = false) }
        launchGeneration(seedArtists = selected)
    }

    fun dismissColdStart() {
        _uiState.update { it.copy(showColdStart = false, isColdStartLoading = false) }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index < 0) return
        playerController.setQueueAndPlay(tracks, startIndex = index, origin = QueueOrigin.WAVE)
    }

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
                    details = emptyMap(),
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
        const val COLD_START_LIKES = 5
        const val COLD_START_HISTORY = 10
        const val COLD_START_ARTIST_LIMIT = 30
        const val MIN_COLD_START_ARTISTS = 3
        const val MAX_COLD_START_ARTISTS = 5
    }
}
