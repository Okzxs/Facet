package com.facet.music.core.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.facet.music.core.source.StreamResolver
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый фасад управления воспроизведением.
 *
 * Сам плеер живёт в [MusicService]: контроллер только подключается к нему
 * через [MediaController] и хранит очередь/режимы, чтобы UI и уведомление
 * работали с одним состоянием.
 */
@Singleton
class PlayerController private constructor(
    private val appContext: Context?,
    private val soundCloudRepository: SoundCloudRepository,
    /** Репозиторий YouTube; null только в старых JVM-тестах без YouTube-сценариев. */
    private val youTubeRepository: YouTubeRepository?,
    private val playHistoryRepository: PlayHistoryRepository,
    /** Репозиторий скачанных треков; null только в JVM-тестах. */
    private val downloadRepository: DownloadRepository?,
    /** Резолвер стрима для внешних источников; null в тестах и легаси-конструкторах. */
    private val streamResolver: StreamResolver?,
    /** Сохранение очереди между запусками; null в JVM-тестах. */
    private val queueRepository: QueueRepository?,
    initialController: MediaController?,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        soundCloudRepository: SoundCloudRepository,
        youTubeRepository: YouTubeRepository,
        playHistoryRepository: PlayHistoryRepository,
        downloadRepository: DownloadRepository,
        streamResolver: StreamResolver,
        queueRepository: QueueRepository,
    ) : this(
        context,
        soundCloudRepository,
        youTubeRepository,
        playHistoryRepository,
        downloadRepository,
        streamResolver,
        queueRepository,
        null,
    )

    internal constructor(
        @ApplicationContext context: Context,
        soundCloudRepository: SoundCloudRepository,
        youTubeRepository: YouTubeRepository,
        playHistoryRepository: PlayHistoryRepository,
        downloadRepository: DownloadRepository,
    ) : this(
        context,
        soundCloudRepository,
        youTubeRepository,
        playHistoryRepository,
        downloadRepository,
        null,
        null,
        null,
    )

    /** Конструктор для JVM-тестов: готовый контроллер без подключения к сервису. */
    internal constructor(
        mediaController: MediaController,
        soundCloudRepository: SoundCloudRepository,
        playHistoryRepository: PlayHistoryRepository,
        youTubeRepository: YouTubeRepository? = null,
        downloadRepository: DownloadRepository? = null,
        streamResolver: StreamResolver? = null,
    ) : this(
        null,
        soundCloudRepository,
        youTubeRepository,
        playHistoryRepository,
        downloadRepository,
        streamResolver,
        null,
        mediaController,
    )

    private val tag = "PlayerController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null
    private var mediaController: MediaController? = null

    // Контроллер — @Singleton и живёт дольше MusicService, поэтому готовность
    // пересоздаётся при каждом переподключении: после смерти сессии прежний
    // MediaController мёртв, а завершённый однажды Deferred уже не оживить.
    @Volatile
    private var controllerReady = CompletableDeferred<MediaController>()
    private var connectAttempts = 0

    // Подключение к MusicService откладываем до первого кадра: создание
    // ExoPlayer, MediaSession и уведомления на главном потоке заметно
    // удлиняло холодный старт. Играть без соединения всё равно нельзя,
    // поэтому перед воспроизведением ensureConnected() сработает сразу.
    @Volatile
    private var connectionRequested = false

    // Исходная очередь до перемешивания (для восстановления исходного порядка)
    private var originalQueue: List<Track> = emptyList()

    // ─── Публичное состояние ──────────────────────────────────────────────────

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // ─── Слушатель событий плеера ────────────────────────────────────────────

    private val playerListener = object : Player.Listener {

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

        override fun onPlayerError(error: PlaybackException) {
            Log.e(tag, "Ошибка воспроизведения MediaController", error)
            _state.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    isPlaying = false,
                    error = error.toFriendlyPlaybackMessage(),
                )
            }
        }
    }

    /**
     * Реакция на смерть или пересоздание [MusicService]. Без неё синглтон навсегда
     * оставался с мёртвым MediaController: кнопки в UI нажимались, звук не шёл.
     * Объявлен до init — иначе connectToService() получил бы неинициализированный null.
     */
    private val sessionListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            Log.w(tag, "Сессия отключена — освобождаем контроллер и подключаемся заново")
            detachController(controller)
            connectToService()
        }
    }

    init {
        if (initialController != null) {
            attachController(initialController)
        } else {
            scope.launch {
                delay(COLD_START_CONNECT_DELAY_MS)
                ensureConnected()
            }
        }
        // Очередь переживает перезапуск: сначала восстанавливаем сохранённую,
        // затем начинаем писать изменения (restore не должен сам себя перезаписать).
        restoreQueueFromDb()
        persistQueue()
    }

    /** Поднимает сохранённую очередь, не начиная воспроизведение. */
    private fun restoreQueueFromDb() {
        val repository = queueRepository ?: return
        scope.launch {
            val snapshot = runCatching { repository.load() }.getOrNull() ?: return@launch
            if (snapshot.tracks.isEmpty()) return@launch
            // Не перетираем очередь, если за время чтения что-то уже играет.
            if (_state.value.currentTrack != null || _state.value.queue.isNotEmpty()) return@launch
            val index = snapshot.currentIndex.coerceIn(0, snapshot.tracks.lastIndex)
            originalQueue = snapshot.tracks
            _state.update {
                it.copy(
                    queue = snapshot.tracks,
                    currentIndex = index,
                    currentTrack = snapshot.tracks[index],
                    isPlaying = false,
                    isLoading = false,
                )
            }
        }
    }

    /** Пишет очередь в Room с задержкой: быстрые правки не дают десятки записей. */
    @OptIn(FlowPreview::class)
    private fun persistQueue() {
        val repository = queueRepository ?: return
        scope.launch {
            _state
                .map { it.queue to it.currentIndex }
                .debounce(QUEUE_PERSIST_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { (queue, index) ->
                    runCatching {
                        if (queue.isEmpty()) repository.clear()
                        else repository.save(queue, index)
                    }
                }
        }
    }

    /**
     * Подключение по требованию. Повторные вызовы безопасны: соединение
     * инициируется один раз, дальше работает штатный реконнект.
     */
    @Synchronized
    private fun ensureConnected() {
        if (mediaController != null || connectionRequested) return
        connectToService()
    }

    private fun connectToService() {
        connectionRequested = true
        val context = appContext ?: return
        val token = SessionToken(
            context,
            ComponentName(context, MusicService::class.java),
        )
        val future = MediaController.Builder(context, token)
            .setListener(sessionListener)
            .buildAsync()
        future.addListener(
            {
                try {
                    attachController(future.get())
                    connectAttempts = 0
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    scheduleReconnect(e)
                } catch (e: Exception) {
                    scheduleReconnect(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Повтор подключения с экспоненциальной задержкой. Провал buildAsync() раньше
     * завершал Deferred исключением навсегда и молча отключал плеер до перезапуска
     * процесса; теперь даём сервису несколько шансов подняться.
     */
    private fun scheduleReconnect(cause: Exception) {
        if (connectAttempts >= MAX_CONNECT_ATTEMPTS) {
            Log.e(tag, "Не удалось подключиться к MusicService после $connectAttempts попыток", cause)
            _state.update { it.copy(error = PLAYER_UNAVAILABLE_MESSAGE) }
            return
        }
        val delayMs = RECONNECT_BASE_DELAY_MS shl connectAttempts++.coerceAtMost(6)
        Log.w(tag, "Переподключение к MusicService через $delayMs мс", cause)
        scope.launch {
            delay(delayMs)
            connectToService()
        }
    }

    private fun attachController(controller: MediaController) {
        mediaController = controller
        controller.addListener(playerListener)
        val isBuffering = controller.playbackState == Player.STATE_BUFFERING
        _state.update {
            it.copy(
                isPlaying = controller.isPlaying,
                isLoading = isBuffering,
                isBuffering = isBuffering,
                playbackState = controller.playbackState,
                playbackSpeed = controller.playbackParameters.speed,
                // Подключение восстановлено — убираем сообщение о недоступном плеере.
                error = if (it.error == PLAYER_UNAVAILABLE_MESSAGE) null else it.error,
            )
        }
        if (!controllerReady.isCompleted) {
            controllerReady.complete(controller)
        }
        // Новый контроллер не знает про детюн и вариацию: возвращаем ему
        // актуальные параметры, иначе после переподключения строй сбрасывался бы.
        applyPlaybackParameters()
    }

    /** Снимает слушатели и освобождает мёртвый контроллер перед переподключением. */
    private fun detachController(controller: MediaController) {
        if (mediaController === controller) {
            mediaController = null
            controllerReady = CompletableDeferred()
        }
        runCatching {
            controller.removeListener(playerListener)
            controller.release()
        }
    }

    private suspend fun awaitController(): MediaController? {
        return try {
            controllerReady.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "MediaController недоступен", e)
            null
        }
    }

    private fun onTrackEnded() {
        val controller = mediaController ?: return
        when (_state.value.repeatMode) {
            RepeatMode.ONE -> {
                controller.seekTo(0)
                controller.play()
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
     * [origin] помечает, откуда очередь пришла: обычный запуск, «Моя волна»
     * или «Волна по треку» — это показывается в шторке очереди.
     */
    fun setQueueAndPlay(
        tracks: List<Track>,
        startIndex: Int = 0,
        origin: QueueOrigin = QueueOrigin.MAIN,
    ) {
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
                queueOrigin = origin,
            )
        }

        playTrackAt(effectiveIndex)
    }

    /**
     * Дописать треки в конец очереди без прерывания воспроизведения.
     * Дубликаты по id отбрасываются.
     */
    fun appendToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val knownIds = _state.value.queue.mapTo(mutableSetOf()) { it.id }
        val additions = tracks.filter { it.id.isNotBlank() && knownIds.add(it.id) }
        if (additions.isEmpty()) return

        originalQueue = if (originalQueue.isEmpty()) {
            _state.value.queue + additions
        } else {
            originalQueue + additions
        }
        _state.update { it.copy(queue = it.queue + additions) }
    }

    /**
     * Поставить трек следующим в очереди, не прерывая воспроизведение.
     *
     * Если очередь пуста (или в ней нет текущего трека), «следующим» некуда —
     * трек просто начинает играть. Дубликат по id не добавляется: прежнее
     * вхождение переносится на позицию сразу после текущего.
     *
     * @return true, если очередь изменилась (трек встал следующим или заиграл);
     * false — если трек пустой или уже является текущим.
     */
    fun playNext(track: Track): Boolean {
        if (track.id.isBlank()) return false
        val state = _state.value
        if (state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            setQueueAndPlay(listOf(track))
            return true
        }

        val insertion = insertNextTrack(state.queue, state.currentIndex, track)
        if (insertion.queue == state.queue) return false

        originalQueue = when {
            originalQueue.isEmpty() -> insertion.queue
            originalQueue.any { it.id == track.id } -> originalQueue
            // При перемешивании исходный порядок хранится отдельно: новый
            // трек уходит в его конец, чтобы восстановление порядка его не потеряло.
            state.isShuffle -> originalQueue + track
            else -> insertion.queue
        }
        _state.update {
            it.copy(queue = insertion.queue, currentIndex = insertion.currentIndex)
        }
        return true
    }

    /**
     * Дописать трек в конец очереди.
     *
     * Если ничего не играет, «конец очереди» недостижим (шторка очереди без
     * текущего трека не открывается), поэтому трек сразу начинает играть.
     *
     * @return true, если трек добавлен; false — если он уже в очереди или без id.
     */
    fun addToQueue(track: Track): Boolean {
        if (track.id.isBlank()) return false
        val state = _state.value
        if (state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            setQueueAndPlay(listOf(track))
            return true
        }
        if (state.queue.any { it.id == track.id }) return false
        appendToQueue(listOf(track))
        return true
    }

    /**
     * Воспроизвести трек по индексу в текущей очереди.
     */
    fun playTrackAt(index: Int) {
        ensureConnected()
        val tracks = _state.value.queue
        if (index !in tracks.indices) return

        val track = tracks[index]
        mediaController?.apply {
            pause()
            stop()
            seekTo(0)
        }
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
     * По умолчанию не зацикливаем: ручной Next при Repeat OFF не должен
     * перескакивать с последнего трека на первый (зацикливание — только при ALL).
     */
    fun playNext(loop: Boolean = false) {
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
    /** [force] — свайп по обложке всегда переключает трек, а не перематывает в 0. */
    fun playPrevious(force: Boolean = false) {
        val controller = mediaController
        if (!force && controller != null && controller.currentPosition > 3000L) {
            controller.seekTo(0)
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
            controller?.seekTo(0)
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
                mediaController?.stop()
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
     * Разрешить URL трека и передать в MediaController.
     */
    private fun resolveAndPlay(track: Track, recordHistory: Boolean = true) {
        ensureConnected()
        resolveJob?.cancel()
        mediaController?.apply {
            pause()
            stop()
            seekTo(0)
        }
        resolveJob = scope.launch {
            // Запись в историю воспроизведения Room: сбой БД не должен ломать воспроизведение
            if (recordHistory) {
                try {
                    playHistoryRepository.addToHistory(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(tag, "Не удалось записать трек в историю", e)
                }
            }

            // Офлайн-режим: если трек скачан, играем его из кэша Media3,
            // не обращаясь к сети (важно при отсутствии интернета).
            val downloaded = try {
                downloadRepository?.getDownloadedTrack(track.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Не удалось проверить скачанный трек", e)
                null
            }

            val streamResult: Result<AudioStreamResult> = when {
                downloaded != null ->
                    Result.success(
                        AudioStreamResult(
                            url = downloaded.mediaUrl,
                            isHls = downloaded.mimeType == MimeTypes.APPLICATION_M3U8,
                            // MIME из Room критичен для офлайн-M4A: по file:// его не угадать
                            mimeType = downloaded.mimeType,
                        ),
                    )
                // Для YouTube у трека хранится URL видео, а не аудио: его резолвит репозиторий
                track.source == Source.YOUTUBE -> {
                    val videoUrl = track.transcodingUrl ?: track.streamUrl
                    val repository = youTubeRepository
                    if (!videoUrl.isNullOrBlank() && repository != null) {
                        repository.resolveStreamUrl(videoUrl)
                    } else {
                        Result.failure(
                            IllegalStateException("Нет YouTube-ссылки для трека: ${track.id}"),
                        )
                    }
                }
                // Внешние источники (Deezer/Apple/Spotify): ссылка может быть превью
                // или отсутствовать — резолвер обновит её либо сматчит трек на YouTube.
                track.source == Source.DEEZER ||
                    track.source == Source.APPLE ||
                    track.source == Source.SPOTIFY -> {
                    val resolver = streamResolver
                    if (resolver != null) {
                        resolver.resolve(track)
                    } else {
                        Result.failure(
                            IllegalStateException("Нет резолвера потока для трека: ${track.id}"),
                        )
                    }
                }
                track.streamUrl != null ->
                    Result.success(
                        AudioStreamResult(
                            url = track.streamUrl,
                            isHls = false,
                            mimeType = defaultMimeType(track.source),
                        ),
                    )
                track.source == Source.SOUNDCLOUD && track.transcodingUrl != null ->
                    soundCloudRepository.resolveStreamUrl(track.transcodingUrl)
                else ->
                    Result.failure(IllegalStateException("Нет ссылки на поток для трека: ${track.id}"))
            }.rethrowCancellation()

            if (downloaded != null) {
                Log.d(tag, "Офлайн-воспроизведение из кэша")
            }

            streamResult
                .onSuccess { result ->
                    Log.d(
                        tag,
                        "Поток готов: isHls=${result.isHls}, mime=${result.mimeType ?: "auto"}",
                    )
                    playStream(track, result)
                }
                .onFailure { error ->
                    // В UI уходит только дружелюбное сообщение, сырое исключение — в лог.
                    // Название трека и ссылку не логируем: id SoundCloud — это URL.
                    val friendlyMessage = error.toFriendlyPlaybackMessage()
                    Log.e(tag, "Ошибка resolve (${track.source}): $friendlyMessage", error)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isBuffering = false,
                            isPlaying = false,
                            error = friendlyMessage,
                        )
                    }
                }
        }
    }

    /**
     * Прямой запуск потока (для вызова с уже готовым AudioStreamResult).
     */
    fun play(track: Track, streamResult: AudioStreamResult) {
        ensureConnected()
        resolveJob?.cancel()
        mediaController?.apply {
            pause()
            stop()
            seekTo(0)
        }
        // Если очередь была пуста — создаём одиночную очередь
        if (_state.value.queue.none { it.id == track.id }) {
            originalQueue = listOf(track)
            _state.update {
                it.copy(
                    queue = listOf(track),
                    currentIndex = 0,
                    currentTrack = track,
                )
            }
        }
        resolveJob = scope.launch {
            playStream(track, streamResult)
        }
    }

    /** MIME по умолчанию для прямых URL, если источник его не сообщил. */
    private fun defaultMimeType(source: Source): String = when (source) {
        Source.YOUTUBE, Source.APPLE -> MimeTypes.AUDIO_MP4
        // Spotify стрима не отдаёт: ссылка приходит с YouTube, это MP3/AAC-поток.
        Source.SPOTIFY -> MimeTypes.AUDIO_MPEG
        else -> MimeTypes.AUDIO_MPEG
    }

    private suspend fun playStream(track: Track, streamResult: AudioStreamResult) {
        val controller = awaitController()
        if (controller == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    isPlaying = false,
                    error = ERROR_PLAYBACK,
                )
            }
            return
        }

        val mimeType = streamResult.mimeType ?: if (streamResult.isHls) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.AUDIO_MPEG
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply {
                track.artworkUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setArtworkUri(it.toUri()) }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamResult.url)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()

        try {
            controller.setMediaItem(mediaItem)
            controller.seekTo(0)
            controller.prepare()
            controller.play()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Не удалось запустить поток (${track.source})", e)
            _state.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    isPlaying = false,
                    error = e.toFriendlyPlaybackMessage(),
                )
            }
            return
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
        ensureConnected()
        // Восстановленная очередь живёт только в состоянии: в контроллере ещё нет
        // медиаэлемента. Первый Play должен запустить трек, а не «нажать play» в пустоту.
        val restored = _state.value
        if (restored.currentTrack != null &&
            restored.queue.isNotEmpty() &&
            mediaController?.mediaItemCount == 0
        ) {
            playTrackAt(restored.currentIndex.coerceIn(0, restored.queue.lastIndex))
            return
        }
        // После ошибки нажатие Play/Pause сразу снимает сообщение и повторяет запуск трека
        if (_state.value.error != null) {
            _state.update { it.copy(error = null) }
            val track = _state.value.currentTrack
            if (track != null) {
                resolveAndPlay(track, recordHistory = false)
            }
            return
        }
        scope.launch {
            val controller = awaitController() ?: return@launch
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    /**
     * Полностью закрыть плеер: остановить звук, очистить очередь и сохранённое
     * состояние. Мини-плеер и экран плеера исчезают, следующий запуск — с нуля.
     */
    fun clearQueue() {
        mediaController?.let { controller ->
            runCatching {
                controller.stop()
                controller.clearMediaItems()
            }
        }
        originalQueue = emptyList()
        _state.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                isLoading = false,
                isBuffering = false,
                playbackState = Player.STATE_IDLE,
                queue = emptyList(),
                currentIndex = -1,
                error = null,
                queueOrigin = QueueOrigin.MAIN,
            )
        }
        scope.launch {
            runCatching { queueRepository?.clear() }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    /**
     * Пауза без переключения (таймер сна). В отличие от [pauseOrResume],
     * повторный вызов ничего не запускает.
     */
    fun pause() {
        scope.launch {
            awaitController()?.pause()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.update { it.copy(playbackSpeed = speed) }
        applyPlaybackParameters()
    }

    /** Сменить строй (детюн): 428…453 Гц, 440 — стандарт. */
    fun setTuning(frequencyHz: Int) {
        if (frequencyHz !in PlaybackTuning.FREQUENCIES) return
        _state.update { it.copy(tuningHz = frequencyHz) }
        applyPlaybackParameters()
    }

    /** Включить вариацию трека (Speed Up, Slowed, Nightcore). */
    fun setVariation(variation: PlaybackVariation) {
        _state.update { it.copy(variation = variation) }
        applyPlaybackParameters()
    }

    /**
     * Отправляет в ExoPlayer итоговые параметры:
     * скорость — базовая × скорость вариации, питч — питч вариации × детюн.
     */
    private fun applyPlaybackParameters() {
        val state = _state.value
        val speed = (state.playbackSpeed * state.variation.speed).coerceIn(0.25f, 4f)
        val pitch = (state.variation.pitch * PlaybackTuning.pitchFactor(state.tuningHz.toFloat()))
            .coerceIn(0.25f, 4f)
        mediaController?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    fun currentPositionMs(): Long = mediaController?.currentPosition ?: 0L

    fun durationMs(): Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    fun bufferedPositionMs(): Long = mediaController?.bufferedPosition?.coerceAtLeast(0L) ?: 0L

    fun bufferedFraction(): Float {
        val duration = durationMs()
        return if (duration > 0) (bufferedPositionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    /** Освобождает контроллер, снимает слушателей и гасит фоновые задачи синглтона. */
    fun release() {
        resolveJob?.cancel()
        mediaController?.let(::detachController)
        controllerReady.cancel()
        scope.cancel()
    }

    private companion object {
        /** Сколько раз пробуем подключиться к MusicService до сообщения об ошибке. */
        const val MAX_CONNECT_ATTEMPTS = 5

        /** Базовая задержка переподключения; удваивается с каждой попыткой. */
        const val RECONNECT_BASE_DELAY_MS = 500L

        /** Пауза перед первым подключением: даём UI отрисовать первый кадр. */
        const val COLD_START_CONNECT_DELAY_MS = 600L

        /** Текст ошибки, который снимается при успешном переподключении. */
        const val PLAYER_UNAVAILABLE_MESSAGE = "Не удалось подключиться к плееру"

        /** Задержка записи очереди в Room: серия быстрых правок даёт одну запись. */
        const val QUEUE_PERSIST_DEBOUNCE_MS = 500L
    }
}

/**
 * Откуда пришла текущая очередь — для подписи в шторке очереди.
 */
enum class QueueOrigin {
    /** Обычный запуск из поиска, медиатеки, плейлиста. */
    MAIN,

    /** Бесконечная «Моя волна» со вкладки. */
    WAVE,

    /** Радио от конкретного трека («Волна по треку»). */
    WAVE_FROM_TRACK,
}

/**
 * Снимок состояния плеера и очереди для UI.
 */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val playbackSpeed: Float = 1.0f,
    /** Строй в герцах: 440 — стандарт, остальные значения — детюн. */
    val tuningHz: Int = 440,
    /** Вариация трека: скорость и питч поверх базовой скорости. */
    val variation: PlaybackVariation = PlaybackVariation.NORMAL,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val queueOrigin: QueueOrigin = QueueOrigin.MAIN,
    val error: String? = null,
)
