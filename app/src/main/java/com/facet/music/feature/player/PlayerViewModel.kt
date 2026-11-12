package com.facet.music.feature.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.download.DownloadManager
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.download.DownloadState
import com.facet.music.core.error.UiError
import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.player.PlaybackVariation
import com.facet.music.core.player.PlayerController
import com.facet.music.core.player.PlayerState
import com.facet.music.core.player.QueueOrigin
import com.facet.music.core.player.SleepTimerState
import com.facet.music.core.player.TrackLikeManager
import com.facet.music.core.player.sleepTimerEndTime
import com.facet.music.core.player.toFriendlyPlaybackError
import com.facet.music.core.service.ServiceType
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.LyricsRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val soundCloudRepository: SoundCloudRepository,
    private val lyricsRepository: LyricsRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val dislikedTrackDao: DislikedTrackDao,
    private val settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
    private val tasteRepository: TasteRepository,
    private val waveRepository: WaveRepository,
    private val downloadManager: DownloadManager,
    private val navigationEvents: NavigationEvents,
    private val trackLikeManager: TrackLikeManager,
) : ViewModel() {

    private val tag = "PlayerViewModel"

    val availableSpeeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val lyricsCache = object : LinkedHashMap<String, LyricsData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsData>): Boolean {
            return size > LYRICS_CACHE_SIZE
        }
    }

    val playerState: StateFlow<PlayerState> = playerController.state

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    val isBuffering: StateFlow<Boolean> = playerController.state
        .map { it.isBuffering }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val isDisliked: StateFlow<Boolean> = playerController.state
        .map { it.currentTrack?.id ?: _uiState.value.track?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId != null) {
                dislikedTrackDao.isDisliked(trackId)
            } else {
                flowOf(false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val downloadState: StateFlow<DownloadState> = playerController.state
        .map { it.currentTrack?.id ?: _uiState.value.track?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId != null) {
                downloadManager.observeState(trackId)
            } else {
                flowOf(DownloadState.NotDownloaded)
            }
        }
        .transformLatest { state ->
            if (state is DownloadState.Error) {
                emit(state)
                delay(ERROR_ICON_DURATION_MS)
                emit(DownloadState.NotDownloaded)
            } else {
                emit(state)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadState.NotDownloaded,
        )

    init {
        observeCurrentTrack()

        viewModelScope.launch {
            playerController.state.collect { state ->
                if (state.currentTrack != null && !state.isLoading) {
                    _uiState.update { it.copy(isResolving = false) }
                }
                if (state.playbackState == androidx.media3.common.Player.STATE_READY) {
                    _uiState.update { it.copy(error = null) }
                } else if (state.error != null) {
                    _uiState.update { it.copy(isResolving = false, error = state.error) }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentTrack() {
        viewModelScope.launch {
            playerController.state
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    if (track != null) {
                        _uiState.update {
                            it.copy(
                                track = track,
                                positionMs = 0L,
                                lyricsData = null,
                                isLyricsLoading = true,
                                lyricsError = null,
                                error = null,
                                downloadError = null,
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            playerController.state
                .map { it.currentTrack }
                .distinctUntilChanged()
                .flatMapLatest { track ->
                    flow<Unit> { if (track != null) loadLyrics(track) }
                }
                .collect()
        }
    }

    fun toggleLike() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        viewModelScope.launch {
            trackLikeManager.toggle(track)
                .onFailure { error ->
                    Log.e(tag, "Не удалось переключить лайк", error)
                    _uiState.update { it.copy(error = UiError.LikeFailed) }
                }
        }
    }

    fun toggleDislike() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        viewModelScope.launch {
            val addingDislike = !isDisliked.value
            val result = runCatching {
                if (addingDislike) {
                    if (isLiked.value) {
                        likedTracksRepository.unlikeTrack(track.id)
                        runCatching { tasteRepository.onUnlike(track) }
                            .rethrowCancellation()
                            .onFailure { error ->
                                Log.w(tag, "Feedback вкусов не записан после дизлайка", error)
                            }
                    }
                    dislikedTrackDao.insert(DislikedTrackEntity(trackId = track.id))
                    runCatching { tasteRepository.onDislike(track) }
                        .rethrowCancellation()
                        .onFailure { error ->
                            Log.w(tag, "Feedback вкусов не записан после дизлайка", error)
                        }
                    runCatching { waveRepository.onTrackDisliked(track) }
                        .rethrowCancellation()
                        .onFailure { error ->
                            Log.w(tag, "Волна не записала дизлайк", error)
                        }
                    if (waveRepository.activeSession.value != null ||
                        playerState.value.queueOrigin == QueueOrigin.WAVE ||
                        playerState.value.queueOrigin == QueueOrigin.WAVE_FROM_TRACK
                    ) {
                        runCatching { playerController.removeUpcomingByArtist(track.artist) }
                        playerController.playNext()
                    }
                } else {
                    dislikedTrackDao.delete(track.id)
                }
            }.rethrowCancellation()
            result.onFailure { error ->
                Log.e(tag, "Не удалось переключить дизлайк", error)
                _uiState.update { it.copy(error = UiError.DislikeFailed) }
            }
        }
    }


    fun blockCurrentArtist() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        viewModelScope.launch {
            runCatching {
                waveRepository.blockArtist(
                    com.facet.music.core.text.MusicNormalizer.normalizeArtist(track.artist),
                )
                playerController.removeUpcomingByArtist(track.artist)
                if (waveRepository.activeSession.value != null ||
                    playerState.value.queueOrigin == QueueOrigin.WAVE ||
                    playerState.value.queueOrigin == QueueOrigin.WAVE_FROM_TRACK
                ) {
                    playerController.playNext()
                }
            }.onFailure { error ->
                Log.e(tag, "Не удалось заблокировать исполнителя", error)
            }
        }
    }

    fun toggleDownload() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        when (downloadState.value) {
            is DownloadState.Downloaded -> Unit
            is DownloadState.InProgress -> downloadManager.cancel(track.id)
            is DownloadState.Error, DownloadState.NotDownloaded -> viewModelScope.launch {
                downloadManager.download(track).onFailure {
                    _uiState.update {
                        it.copy(downloadError = DownloadMessages.SOURCE_NOT_FOUND)
                    }
                }
            }
        }
    }

    fun consumeDownloadError() {
        _uiState.update { it.copy(downloadError = null) }
    }

    fun findOnYouTube(track: Track) {
        val query = listOf(track.artist.trim(), track.title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        if (query.isNotBlank()) {
            navigationEvents.requestSearch(query = query, service = ServiceType.YOUTUBE)
        }
    }

    fun playTrackWithQueue(
        track: Track,
        queue: List<Track>,
        resetWave: Boolean = true,
        origin: QueueOrigin = QueueOrigin.MAIN,
    ) {
        val targetQueue = if (queue.isNotEmpty()) queue else listOf(track)
        val startIndex = targetQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _uiState.update {
            it.copy(
                track = track,
                positionMs = 0L,
                isResolving = true,
                error = null,
                lyricsData = null,
                isLyricsLoading = true,
                lyricsError = null,
            )
        }

        viewModelScope.launch {
            loadLyrics(track)
        }

        if (resetWave) {
            viewModelScope.launch {
                waveRepository.reset()
            }
        }

        playerController.setQueueAndPlay(targetQueue, startIndex, origin)
    }

    fun clearQueue() = playerController.clearQueue()

    fun startWaveFromTrack(track: Track) {
        if (_uiState.value.isRadioLoading) return
        _uiState.update { it.copy(isRadioLoading = true) }
        viewModelScope.launch {
            val result = waveRepository.startRadio(track)
                .mapCatching { waveRepository.nextBatch(size = RADIO_BATCH).getOrThrow() }
            _uiState.update { it.copy(isRadioLoading = false) }
            result
                .onSuccess { batch ->
                    val related = batch.tracks.filter { it.id != track.id }
                    playTrackWithQueue(
                        track = track,
                        queue = listOf(track) + related,
                        resetWave = false,
                        origin = QueueOrigin.WAVE_FROM_TRACK,
                    )
                }
                .onFailure { error ->
                    Log.e(tag, "Не удалось запустить волну от трека", error)
                }
        }
    }

    fun startWaveFromArtist(artist: String) {
        if (artist.isBlank() || _uiState.value.isRadioLoading) return
        _uiState.update { it.copy(isRadioLoading = true) }
        viewModelScope.launch {
            val result = waveRepository.startRadioForArtist(artist)
                .mapCatching { waveRepository.nextBatch(size = RADIO_BATCH).getOrThrow() }
            _uiState.update { it.copy(isRadioLoading = false) }
            result.onSuccess { batch ->
                if (batch.tracks.isNotEmpty()) {
                    playTrackWithQueue(
                        track = batch.tracks.first(),
                        queue = batch.tracks,
                        resetWave = false,
                        origin = QueueOrigin.WAVE_FROM_TRACK,
                    )
                }
            }.onFailure { error ->
                Log.e(tag, "Не удалось запустить волну по артисту", error)
            }
        }
    }

    fun loadAndPlay(track: Track) {
        playTrackWithQueue(track, listOf(track))
    }

    fun playNext() {
        playerController.playNext()
    }

    fun playNext(track: Track): Boolean = playerController.playNext(track)

    fun addToQueue(track: Track): Boolean = playerController.addToQueue(track)

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)

    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endsAt = sleepTimerEndTime(System.currentTimeMillis(), minutes)
        _sleepTimer.value = SleepTimerState(minutes = minutes, endsAtMillis = endsAt)
        sleepTimerJob = viewModelScope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            playerController.pause()
            _sleepTimer.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimer.value = null
    }

    fun playPrevious(force: Boolean = false) {
        playerController.playPrevious(force)
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
        if (_uiState.value.lyricsData == null && !_uiState.value.isLyricsLoading) {
            _uiState.update { it.copy(isLyricsLoading = true, lyricsError = null) }
            viewModelScope.launch {
                loadLyrics(currentTrack)
            }
        }
    }

    private suspend fun loadLyrics(track: Track) {
        Log.d("LRCLIB", "PlayerViewModel.loadLyrics для трека '${track.title}' (${track.artist})")
        val key = lyricsKey(track)
        lyricsCache[key]?.let { cached ->
            _uiState.update { it.copy(lyricsData = cached, isLyricsLoading = false, lyricsError = null) }
            prefetchNextLyrics()
            return
        }
        lyricsRepository.getLyrics(title = track.title, artist = track.artist, durationMs = track.durationMs)
            .onSuccess { result ->
                val data = withContext(Dispatchers.Default) { buildLyricsData(result) }
                Log.d("LRCLIB", "PlayerViewModel: тексты успешно получены (isSynced=${data.isSynced}, строк=${data.lines.size})")
                synchronized(lyricsCache) { lyricsCache[key] = data }
                _uiState.update { it.copy(lyricsData = data, isLyricsLoading = false, lyricsError = null) }
                prefetchNextLyrics()
            }
            .onFailure { error ->
                val friendlyError = error.toFriendlyPlaybackError(UiError.Lyrics)
                Log.w("LRCLIB", "PlayerViewModel: не удалось загрузить тексты: $friendlyError", error)
                _uiState.update {
                    it.copy(lyricsData = null, isLyricsLoading = false, lyricsError = friendlyError)
                }
            }
    }

    private fun lyricsKey(track: Track): String = "${track.artist}|${track.title}"

    private fun prefetchNextLyrics() {
        val state = playerState.value
        val next = state.queue.getOrNull(state.currentIndex + 1) ?: return
        val key = lyricsKey(next)
        if (synchronized(lyricsCache) { lyricsCache.containsKey(key) }) return
        viewModelScope.launch {
            lyricsRepository.getLyrics(title = next.title, artist = next.artist, durationMs = next.durationMs)
                .onSuccess { result ->
                    val data = withContext(Dispatchers.Default) { buildLyricsData(result) }
                    synchronized(lyricsCache) { lyricsCache[key] = data }
                }
        }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(error = null) }
        playerController.pauseOrResume()
    }

    fun seekTo(fraction: Float) {
        val duration = playerController.durationMs().takeIf { it > 0 }
            ?: playerState.value.currentTrack?.durationMs?.takeIf { it > 0 }
            ?: playerState.value.queue.getOrNull(playerState.value.currentIndex)?.durationMs
            ?: 0L
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

    fun setTuning(frequencyHz: Int) {
        playerController.setTuning(frequencyHz)
    }

    fun setVariation(variation: PlaybackVariation) {
        playerController.setVariation(variation)
    }

    fun currentFraction(): Float {
        val duration = playerController.durationMs()
        return if (duration > 0) playerController.currentPositionMs().toFloat() / duration else 0f
    }

    fun currentPositionMs(): Long = playerController.currentPositionMs()
    fun durationMs(): Long = playerController.durationMs()

    fun bufferedPositionMs(): Long = playerController.bufferedPositionMs()
    fun bufferedFraction(): Float = playerController.bufferedFraction()

    private companion object {
        const val RADIO_BATCH = 20

        const val ERROR_ICON_DURATION_MS = 1_000L

        const val LYRICS_CACHE_SIZE = 20
    }
}

data class PlayerUiState(
    val track: Track? = null,
    val positionMs: Long = 0L,
    val isResolving: Boolean = false,
    val error: UiError? = null,
    val lyricsData: LyricsData? = null,
    val isLyricsLoading: Boolean = false,
    val lyricsError: UiError? = null,
    val downloadError: String? = null,
    val isRadioLoading: Boolean = false,
)

