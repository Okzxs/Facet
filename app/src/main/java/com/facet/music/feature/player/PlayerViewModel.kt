package com.facet.music.feature.player

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.download.DownloadManager
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.download.DownloadState
import com.facet.music.core.location.LocationProvider
import com.facet.music.core.navigation.NavigationEvents
import com.facet.music.core.player.ERROR_LYRICS
import com.facet.music.core.player.PlayerController
import com.facet.music.core.player.PlayerState
import com.facet.music.core.player.SleepTimerState
import com.facet.music.core.player.sleepTimerEndTime
import com.facet.music.core.player.toFriendlyPlaybackMessage
import com.facet.music.core.service.ServiceType
import com.facet.music.core.util.SeasonProvider
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.LyricsResult
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.LyricsRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val dislikedTrackDao: DislikedTrackDao,
    private val playHistoryRepository: PlayHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
    private val locationProvider: LocationProvider,
    private val tasteRepository: TasteRepository,
    private val waveRepository: WaveRepository,
    private val downloadManager: DownloadManager,
    private val navigationEvents: NavigationEvents,
) : ViewModel() {

    private val tag = "PlayerViewModel"

    private var feedbackTrack: Track? = null
    private var feedbackPlayedMs = 0L
    private var feedbackLastTickAt = 0L
    private var feedbackWasPlaying = false
    private var waveRefillJob: Job? = null

    // Доступные скорости воспроизведения (по референсу MD3 плеера)
    val availableSpeeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f)

    // ─── UI-состояние ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Состояние плеера (isPlaying, currentTrack, playbackSpeed, queue, shuffle, repeat) */
    val playerState: StateFlow<PlayerState> = playerController.state

    /** Настройки оформления: флаг динамических цветов обложки и акцент. */
    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    /** Флаг буферизации ExoPlayer */
    val isBuffering: StateFlow<Boolean> = playerController.state
        .map { it.isBuffering }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

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

    /** Наблюдение за дизлайком текущего трека из БД Room. */
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

    /**
     * Состояние скачивания текущего трека.
     *
     * Ошибка/отмена показывается крестиком ровно секунду, затем UI снова
     * предлагает скачать трек. Если за это время начнётся новая попытка,
     * transformLatest отменит таймер и покажет её прогресс.
     */
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

        // Сброс флага isResolving и ошибок при готовности плеера
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

        observeTasteFeedback()
        observeWaveRefill()
    }

    /**
     * Реакция на смену текущего трека: сброс позиции и ошибок + загрузка текста.
     *
     * Текст грузится отдельным потоком через flatMapLatest: раньше suspend-вызов
     * loadLyrics жил внутри collect и блокировал обработку следующей смены трека,
     * пока LRCLIB отвечал. Теперь старый запрос отменяется при переключении.
     */
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
                                lyrics = null,
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
                    // Эмиссий нет: поток нужен только как отменяемая единица работы.
                    flow<Unit> { if (track != null) loadLyrics(track) }
                }
                .collect()
        }
    }

    // ─── Публичные методы ─────────────────────────────────────────────────────

    /**
     * Переключить статус избранного (лайк) для текущего трека.
     * При постановке лайка сохраняем последнюю геопозицию и текущий сезон —
     * они питают секцию «Воспоминания».
     */
    fun toggleLike() {
        val track = playerState.value.currentTrack ?: _uiState.value.track ?: return
        viewModelScope.launch {
            val addingLike = !isLiked.value
            // Сбой БД не должен ронять приложение: показываем ошибку в UI.
            val result = runCatching {
                val point = if (addingLike) locationProvider.lastKnownLocation() else null
                likedTracksRepository.toggleLike(
                    track = track,
                    latitude = point?.latitude,
                    longitude = point?.longitude,
                    season = if (addingLike) SeasonProvider.currentSeason() else null,
                )
            }.rethrowCancellation()
            result.onFailure { error ->
                Log.e(tag, "Не удалось переключить лайк", error)
                _uiState.update { it.copy(error = LIKE_FAILED_MESSAGE) }
                return@launch
            }
            val likedNow = result.getOrThrow()
            // Лайк и дизлайк взаимоисключающие: поставили лайк — снимаем дизлайк.
            if (likedNow && isDisliked.value) {
                runCatching { dislikedTrackDao.delete(track.id) }
                    .rethrowCancellation()
                    .onFailure { error ->
                        Log.w(tag, "Не удалось снять дизлайк после лайка", error)
                    }
            }
            // Feedback для вкусов некритичен: его сбой логируем, лайк уже сохранён.
            runCatching {
                if (likedNow) {
                    tasteRepository.onLike(track)
                } else {
                    tasteRepository.onUnlike(track)
                }
            }.rethrowCancellation().onFailure { error ->
                Log.w(tag, "Feedback вкусов не записан", error)
            }
        }
    }

    /**
     * Переключить дизлайк текущего трека.
     * Дизлайк автоматически снимает лайк: трек не должен быть
     * одновременно в избранном и в списке исключённых.
     */
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
                } else {
                    dislikedTrackDao.delete(track.id)
                }
            }.rethrowCancellation()
            result.onFailure { error ->
                Log.e(tag, "Не удалось переключить дизлайк", error)
                _uiState.update { it.copy(error = DISLIKE_FAILED_MESSAGE) }
            }
        }
    }

    /**
     * Кнопка скачивания в плеере.
     * Не скачан / ошибка → ставим в очередь, в процессе → отменяем,
     * уже скачан → ничего не делаем (файл остаётся офлайн).
     */
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

    /** Snackbar показан — сбрасываем сообщение, чтобы оно не повторялось. */
    fun consumeDownloadError() {
        _uiState.update { it.copy(downloadError = null) }
    }

    /**
     * Умный редирект: отправить в поиск запрос «Артист - Название»
     * с уже выбранным YouTube. Навигацию выполняет AppNavGraph.
     */
    fun findOnYouTube(track: Track) {
        val query = listOf(track.artist.trim(), track.title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        if (query.isNotBlank()) {
            navigationEvents.requestSearch(query = query, service = ServiceType.YOUTUBE)
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
                positionMs = 0L,
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

        // Ручной запуск из поиска/медиатеки завершает текущую волну
        waveRefillJob?.cancel()
        viewModelScope.launch {
            waveRepository.reset()
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

    /** Поставить трек следующим в очереди; false — трек уже играет. */
    fun playNext(track: Track): Boolean = playerController.playNext(track)

    /** Дописать трек в очередь; false — он уже там. */
    fun addToQueue(track: Track): Boolean = playerController.addToQueue(track)

    // ─── Таймер сна ───────────────────────────────────────────────────────────

    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)

    /** Активный таймер сна или null. */
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null

    /**
     * Запустить таймер сна: через [minutes] минут воспроизведение встанет
     * на паузу. Повторный выбор заменяет прежний таймер, а не суммируется.
     */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endsAt = sleepTimerEndTime(System.currentTimeMillis(), minutes)
        _sleepTimer.value = SleepTimerState(minutes = minutes, endsAtMillis = endsAt)
        sleepTimerJob = viewModelScope.launch {
            // Точная задержка, а не округлённые минуты: иначе пауза приходит
            // на несколько секунд позже выбранного времени.
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            playerController.pause()
            _sleepTimer.value = null
        }
    }

    /** Выключить таймер сна, не трогая воспроизведение. */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimer.value = null
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
                val friendlyMessage = error.toFriendlyPlaybackMessage(ERROR_LYRICS)
                Log.w("LRCLIB", "PlayerViewModel: не удалось загрузить тексты: $friendlyMessage", error)
                _uiState.update {
                    it.copy(lyrics = null, isLyricsLoading = false, lyricsError = friendlyMessage)
                }
            }
    }

    fun togglePlayPause() {
        // Сразу убираем сообщение об ошибке из UI; контроллер при необходимости повторит запуск
        _uiState.update { it.copy(error = null) }
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

    fun bufferedPositionMs(): Long = playerController.bufferedPositionMs()
    fun bufferedFraction(): Float = playerController.bufferedFraction()

    /**
     * Накопительное прослушивание: при смене трека отправляем
     * дослушивание (>80%) или ранний скип (<30 секунд) в профиль вкусов.
     */
    private fun observeTasteFeedback() {
        viewModelScope.launch {
            playerController.state.collect { state ->
                val now = SystemClock.elapsedRealtime()
                if (feedbackWasPlaying && feedbackLastTickAt > 0L) {
                    feedbackPlayedMs += (now - feedbackLastTickAt).coerceAtLeast(0L)
                }
                feedbackLastTickAt = now
                feedbackWasPlaying = state.isPlaying

                val track = state.currentTrack
                if (track?.id != feedbackTrack?.id) {
                    val previous = feedbackTrack
                    val playedMs = feedbackPlayedMs
                    feedbackTrack = track
                    feedbackPlayedMs = 0L
                    if (previous != null && playedMs > 0L) {
                        recordTasteFeedback(previous, playedMs)
                    }
                }
            }
        }
    }

    private fun recordTasteFeedback(track: Track, playedMs: Long) {
        val fraction = if (track.durationMs > 0L) {
            (playedMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

        viewModelScope.launch {
            when {
                fraction > FINISHED_THRESHOLD -> tasteRepository.onTrackFinished(track, fraction)
                playedMs < SKIP_THRESHOLD_MS -> tasteRepository.onSkip(track, playedMs)
            }
        }
    }

    /**
     * Подгружает следующую пачку заранее, пока в очереди волны
     * остаётся несколько треков.
     */
    private fun observeWaveRefill() {
        viewModelScope.launch {
            playerController.state.collect { state ->
                val session = waveRepository.activeSession.value ?: return@collect
                if (session.isTerminated) return@collect
                if (state.currentTrack == null || state.queue.isEmpty()) return@collect

                val remaining = state.queue.lastIndex - state.currentIndex
                if (remaining > WAVE_REFILL_THRESHOLD) return@collect
                if (waveRefillJob?.isActive == true) return@collect

                waveRefillJob = viewModelScope.launch {
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
        }
    }

    private companion object {
        const val FINISHED_THRESHOLD = 0.8f
        const val SKIP_THRESHOLD_MS = 30_000L
        const val WAVE_REFILL_THRESHOLD = 3
        const val WAVE_REFILL_BATCH = 12

        /** Сколько держать крестик ошибки/отмены перед возвратом иконки скачивания. */
        const val ERROR_ICON_DURATION_MS = 1_000L

        /** Сообщение при сбое БД на лайке — показывается вместо краша. */
        const val LIKE_FAILED_MESSAGE = "Не удалось обновить лайк: база данных недоступна"

        /** Сообщение при сбое БД на дизлайке. */
        const val DISLIKE_FAILED_MESSAGE = "Не удалось обновить дизлайк: база данных недоступна"
    }
}

/**
 * UI-состояние экрана плеера (ошибки, загрузка URL, тексты, позиция).
 */
data class PlayerUiState(
    val track: Track? = null,
    val positionMs: Long = 0L,
    val isResolving: Boolean = false,
    val error: String? = null,
    val lyrics: LyricsResult? = null,
    val isLyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val downloadError: String? = null,
)

