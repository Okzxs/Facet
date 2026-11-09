package com.facet.music.core.player

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.core.text.MusicNormalizer
import com.facet.music.data.model.Track
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фасад плеера. Список треков живёт здесь для UI, но очередь воспроизведения
 * зеркалится в ExoPlayer: gapless, предзагрузка и системная шторка работают
 * без пересоздания медиаэлемента.
 */
@Singleton
class PlayerController @Inject constructor(
    private val connection: PlayerConnection,
    private val playbackResolver: PlaybackResolver,
    private val queueRepository: QueueRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val tag = "PlayerController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var attachedPlayer: Player? = null
    private var originalQueue: List<Track> = emptyList()
    private var resolveJob: Job? = null
    private var preloadJob: Job? = null

    @Volatile
    private var audioFocusPausedAt: Long? = null

    @Volatile
    private var errorStreak = 0

    @Volatile
    private var resolving = false

    @Volatile
    private var errorPositionMs: Long = 0L

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorStreak = 0
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) resolving = false
            _state.update {
                it.copy(
                    isLoading = buffering || resolving,
                    isBuffering = buffering || resolving,
                    playbackState = playbackState,
                    error = if (playbackState == Player.STATE_READY) null else it.error,
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            errorPositionMs = 0L
            syncFromPlayer()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                audioFocusPausedAt = null
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                audioFocusPausedAt = System.currentTimeMillis()
            } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                audioFocusPausedAt = null
            }
        }
    }

    init {
        scope.launch {
            connection.player.collect { attachPlayer(it) }
        }
        scope.launch { connection.connect() }
        restoreQueueFromDb()
    }

    private fun attachPlayer(player: Player?) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = player
        if (player == null) {
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            return
        }
        player.addListener(playerListener)
        syncFromPlayer()
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                playbackState = player.playbackState,
                playbackSpeed = player.playbackParameters.speed,
                isBuffering = player.playbackState == Player.STATE_BUFFERING || resolving,
                isLoading = player.playbackState == Player.STATE_BUFFERING || resolving,
            )
        }
        applyPlaybackParameters()
    }

    private fun syncFromPlayer() {
        val player = attachedPlayer ?: return
        val tracks = (0 until player.mediaItemCount)
            .mapNotNull { TrackMediaItem.trackOf(player.getMediaItemAt(it)) }
        tracks.forEach(playbackResolver::remember)
        val index = player.currentMediaItemIndex
        val current = TrackMediaItem.trackOf(player.currentMediaItem)
            ?: tracks.getOrNull(index)
        _state.update {
            if (tracks.isNotEmpty()) {
                it.copy(
                    queue = tracks,
                    currentIndex = index,
                    currentTrack = current,
                    playbackState = player.playbackState,
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING || resolving,
                    isLoading = player.playbackState == Player.STATE_BUFFERING || resolving,
                )
            } else {
                it.copy(
                    playbackState = player.playbackState,
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING || resolving,
                    isLoading = player.playbackState == Player.STATE_BUFFERING || resolving,
                )
            }
        }
    }

    private suspend fun awaitPlayer(): Player? = connection.connect()

    private fun defaultMime(track: Track): String = TrackMediaItem.defaultMimeType(track)

    private fun mediaItems(queue: List<Track>): List<MediaItem> =
        queue.map { track -> TrackMediaItem.create(track, defaultMime(track)) }

    private fun mediaItems(
        queue: List<Track>,
        currentIndex: Int,
        currentMime: String?,
        nextIndex: Int? = null,
        nextMime: String? = null,
    ): List<MediaItem> = queue.mapIndexed { index, track ->
        val mime = when (index) {
            currentIndex -> currentMime
            nextIndex -> nextMime
            else -> null
        }
        TrackMediaItem.create(
            track = track,
            mimeType = mime ?: defaultMime(track),
        )
    }

    private fun repeatMode() = when (_state.value.repeatMode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

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

        resolving = true
        _state.update {
            it.copy(
                queue = effectiveQueue,
                currentIndex = effectiveIndex,
                currentTrack = effectiveQueue[effectiveIndex],
                queueOrigin = origin,
                isBuffering = true,
                isLoading = true,
                error = null,
            )
        }

        scope.launch {
            val player = awaitPlayer() ?: run {
                resolving = false
                _state.update { it.copy(isBuffering = false, isLoading = false) }
                return@launch
            }
            player.setMediaItems(mediaItems(effectiveQueue), effectiveIndex, 0L)
            player.repeatMode = repeatMode()
            player.shuffleModeEnabled = false
            val stream = playbackResolver.resolve(effectiveQueue[effectiveIndex]).getOrNull()
            if (stream != null) {
                player.setMediaItems(
                    mediaItems(effectiveQueue, effectiveIndex, stream.mimeType),
                    effectiveIndex,
                    0L,
                )
            }
            player.prepare()
            player.play()
            preloadNext()
            preloadNext()
        }
    }

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
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            player.addMediaItems(
                player.mediaItemCount,
                additions.map { TrackMediaItem.create(it) },
            )
        }
    }

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
            state.isShuffle -> originalQueue + track
            else -> insertion.queue
        }
        _state.update {
            it.copy(queue = insertion.queue, currentIndex = insertion.currentIndex)
        }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            val insertAt = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            player.addMediaItems(insertAt, listOf(TrackMediaItem.create(track)))
        }
        return true
    }

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

    fun playTrackAt(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        resolving = true
        _state.update { it.copy(isBuffering = true, isLoading = true, error = null) }
        scope.launch {
            val player = awaitPlayer() ?: run {
                resolving = false
                _state.update { it.copy(isBuffering = false, isLoading = false) }
                return@launch
            }
            ensurePlayerQueue(player, queue, index)
            val stream = playbackResolver.resolve(queue[index]).getOrNull()
            val currentMime = player.getMediaItemAt(index).localConfiguration?.mimeType
            if (stream != null && stream.mimeType != currentMime) {
                player.setMediaItems(
                    mediaItems(queue, index, stream.mimeType),
                    index,
                    0L,
                )
            }
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            preloadNext()
        }
    }

    fun playNext(loop: Boolean = false) {
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            when {
                player.hasNextMediaItem() -> player.seekToNextMediaItem()
                loop || _state.value.repeatMode == RepeatMode.ALL -> player.seekTo(0, 0L)
                else -> return@launch
            }
            player.prepare()
            player.play()
        }
    }

    fun playPrevious(force: Boolean = false) {
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            if (!force && player.currentPosition > 3000L) {
                player.seekTo(0L)
                return@launch
            }
            when {
                player.hasPreviousMediaItem() -> player.seekToPreviousMediaItem()
                _state.value.repeatMode == RepeatMode.ALL ->
                    player.seekTo((player.mediaItemCount - 1).coerceAtLeast(0), 0L)
                else -> player.seekTo(0L)
            }
            player.prepare()
            player.play()
        }
    }

    fun hasNext(): Boolean {
        val state = _state.value
        return state.currentIndex + 1 in state.queue.indices ||
            state.repeatMode == RepeatMode.ALL
    }

    fun hasPrevious(): Boolean {
        val state = _state.value
        return state.currentIndex - 1 in state.queue.indices ||
            state.repeatMode == RepeatMode.ALL
    }

    fun toggleShuffle() {
        val currentQueue = _state.value.queue
        val currentTrack = _state.value.currentTrack
        val newShuffle = !_state.value.isShuffle
        val newQueue: List<Track>
        val newIndex: Int
        if (newShuffle) {
            if (originalQueue.isEmpty() || originalQueue.size != currentQueue.size) {
                originalQueue = currentQueue
            }
            val others = currentQueue.filter { it.id != currentTrack?.id }.shuffled()
            newQueue = if (currentTrack != null) listOf(currentTrack) + others else others
            newIndex = 0
        } else {
            newQueue = if (originalQueue.isNotEmpty()) originalQueue else currentQueue
            newIndex = newQueue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }
        _state.update {
            it.copy(isShuffle = newShuffle, queue = newQueue, currentIndex = newIndex)
        }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            val position = player.currentPosition
            val wasPlaying = player.playWhenReady
            player.setMediaItems(mediaItems(newQueue), newIndex, position)
            player.shuffleModeEnabled = false
            val stream = playbackResolver.resolve(newQueue[newIndex]).getOrNull()
            if (stream != null) applyMime(player, newIndex, stream.mimeType)
            player.prepare()
            if (wasPlaying) player.play()
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(repeatMode = nextMode) }
        scope.launch {
            awaitPlayer()?.repeatMode = repeatMode()
        }
    }

    fun removeTrackAt(index: Int) {
        val currentQueue = _state.value.queue
        if (index !in currentQueue.indices) return
        val removed = currentQueue[index]
        originalQueue = originalQueue.filter { it.id != removed.id }
        val newQueue = currentQueue.filterIndexed { i, _ -> i != index }
        _state.update {
            if (newQueue.isEmpty()) {
                it.copy(queue = emptyList(), currentIndex = -1, currentTrack = null)
            } else {
                it.copy(queue = newQueue)
            }
        }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            if (index < player.mediaItemCount) player.removeMediaItem(index)
            if (player.mediaItemCount == 0) player.clearMediaItems()
        }
    }


    /** Удаляет ещё не проигранные треки артиста, не прерывая текущий. */
    fun removeUpcomingByArtist(artist: String): Int {
        val target = MusicNormalizer.normalizeArtist(artist)
        if (target.isBlank()) return 0
        val state = _state.value
        val current = state.currentIndex.coerceAtLeast(0)
        val removedIndices = state.queue.indices.filter { index ->
            index > current && MusicNormalizer.normalizeArtist(state.queue[index].artist) == target
        }
        if (removedIndices.isEmpty()) return 0
        val removedIds = removedIndices.mapTo(hashSetOf()) { state.queue[it].id }
        originalQueue = originalQueue.filterNot { it.id in removedIds }
        val newQueue = state.queue.filterIndexed { index, _ -> index !in removedIndices }
        _state.update { it.copy(queue = newQueue) }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            removedIndices.asReversed().forEach { index ->
                if (index < player.mediaItemCount) player.removeMediaItem(index)
            }
        }
        return removedIndices.size
    }

    /** Заменяет всё после текущего новой пачкой, текущий трек не трогает. */
    fun replaceUpcoming(tracks: List<Track>) {
        val state = _state.value
        if (state.queue.isEmpty() || state.currentIndex !in state.queue.indices) return
        val keep = state.queue.take(state.currentIndex + 1)
        val keptIds = keep.mapTo(hashSetOf()) { it.id }
        val additions = tracks.filter { it.id.isNotBlank() && keptIds.add(it.id) }
        originalQueue = keep + additions
        _state.update { it.copy(queue = keep + additions) }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            val from = state.currentIndex + 1
            if (from < player.mediaItemCount) {
                player.removeMediaItems(from, player.mediaItemCount - from)
            }
            if (additions.isNotEmpty()) {
                player.addMediaItems(
                    player.mediaItemCount,
                    additions.map { TrackMediaItem.create(it) },
                )
            }
        }
    }

    /**
     * Повтор после ошибки: не начинает трек с нуля, а возвращается к позиции,
     * на которой плеер остановился.
     */
    fun retryAfterError() {
        val position = errorPositionMs.coerceAtLeast(0L)
        errorStreak = 0
        resolving = true
        _state.update { it.copy(error = null, isBuffering = true, isLoading = true) }
        scope.launch {
            val player = awaitPlayer() ?: return@launch
            player.seekTo(position)
            player.prepare()
            player.play()
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        val player = attachedPlayer
        errorPositionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        errorStreak++
        val canAdvance = player != null &&
            (player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ALL)
        if (player != null && errorStreak < MAX_CONSECUTIVE_ERRORS && canAdvance) {
            _state.update { it.copy(error = null, isBuffering = true, isLoading = true) }
            if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
            player.prepare()
            player.play()
            return
        }
        player?.pause()
        resolving = false
        _state.update {
            it.copy(
                isLoading = false,
                isBuffering = false,
                error = error.toFriendlyPlaybackMessage(),
            )
        }
        Log.e(tag, "Плеер остановлен после $errorStreak ошибок: ${error.errorCodeName}")
    }

    fun pauseOrResume() {
        if (_state.value.error != null) {
            retryAfterError()
            return
        }
        if (_state.value.isBuffering) return
        scope.launch {
            val player = awaitPlayer()
            if (player == null) {
                _state.update { it.copy(error = PLAYER_UNAVAILABLE_MESSAGE) }
                return@launch
            }
            if (player.mediaItemCount == 0) {
                val snapshot = runCatching { queueRepository.load() }.getOrNull()
                if (snapshot != null && snapshot.tracks.isNotEmpty()) {
                    val index = snapshot.currentIndex.coerceIn(0, snapshot.tracks.lastIndex)
                    originalQueue = snapshot.tracks
                    _state.update {
                        it.copy(
                            queue = snapshot.tracks,
                            currentIndex = index,
                            currentTrack = snapshot.tracks[index],
                            error = null,
                            isBuffering = true,
                            isLoading = true,
                        )
                    }
                    val currentMime = playbackResolver
                        .resolve(snapshot.tracks[index])
                        .getOrNull()
                        ?.mimeType
                    player.setMediaItems(
                        mediaItems(snapshot.tracks, index, currentMime),
                        index,
                        snapshot.positionMs,
                    )
                    player.repeatMode = repeatMode()
                    player.prepare()
                    player.play()
                    return@launch
                }
                if (_state.value.queue.isNotEmpty()) {
                    val index = _state.value.currentIndex.coerceIn(0, _state.value.queue.lastIndex)
                    player.setMediaItems(
                        mediaItems(_state.value.queue),
                        index,
                        0L,
                    )
                    player.prepare()
                    player.play()
                    return@launch
                }
            }
            if (player.playbackState == Player.STATE_IDLE) {
                resolving = true
                _state.update { it.copy(isBuffering = true, isLoading = true) }
                if (player.mediaItemCount > 0) {
                    val queue = _state.value.queue
                    val index = player.currentMediaItemIndex
                    queue.getOrNull(index)?.let { track ->
                        val position = player.currentPosition
                        val stream = playbackResolver.resolve(track).getOrNull()
                        val currentMime = player.getMediaItemAt(index).localConfiguration?.mimeType
                        if (stream != null && stream.mimeType != currentMime) {
                            player.setMediaItems(
                                mediaItems(queue, index, stream.mimeType),
                                index,
                                position,
                            )
                        } else if (position > 0L) {
                            player.seekTo(index, position)
                        }
                    }
                }
                player.prepare()
            }
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun clearQueue() {
        resolving = false
        scope.launch {
            val player = awaitPlayer()
            player?.clearMediaItems()
            player?.stop()
            runCatching { queueRepository.clear() }
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
    }

    fun seekTo(positionMs: Long) {
        scope.launch { awaitPlayer()?.seekTo(positionMs) }
    }

    fun pause() {
        scope.launch { awaitPlayer()?.pause() }
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.update { it.copy(playbackSpeed = speed) }
        applyPlaybackParameters()
    }

    fun setTuning(frequencyHz: Int) {
        if (frequencyHz !in PlaybackTuning.FREQUENCIES) return
        _state.update { it.copy(tuningHz = frequencyHz) }
        applyPlaybackParameters()
    }

    fun setVariation(variation: PlaybackVariation) {
        _state.update { it.copy(variation = variation) }
        applyPlaybackParameters()
    }

    private fun applyPlaybackParameters() {
        val state = _state.value
        val speed = (state.playbackSpeed * state.variation.speed).coerceIn(0.25f, 4f)
        val pitch = (state.variation.pitch * PlaybackTuning.pitchFactor(state.tuningHz.toFloat()))
            .coerceIn(0.25f, 4f)
        attachedPlayer?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    fun currentPositionMs(): Long = attachedPlayer?.currentPosition ?: 0L

    fun durationMs(): Long = attachedPlayer?.duration?.coerceAtLeast(0L) ?: 0L

    fun bufferedPositionMs(): Long = attachedPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L

    fun bufferedFraction(): Float {
        val duration = durationMs()
        return if (duration > 0) (bufferedPositionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    /** Разрешить следующий трек заранее, чтобы переход не ждал сеть. */
    fun preloadNext() {
        val state = _state.value
        if (state.queue.isEmpty()) return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            val player = attachedPlayer ?: return@launch
            val currentIndex = player.currentMediaItemIndex
                .takeIf { it in state.queue.indices } ?: return@launch
            val nextIndex = (currentIndex + 1).takeIf { it in state.queue.indices }
                ?: return@launch
            val stream = playbackResolver.resolve(state.queue[nextIndex]).getOrNull()
                ?: return@launch
            val currentItem = player.getMediaItemAt(currentIndex)
            val currentMime = currentItem.localConfiguration?.mimeType
            val nextItem = player.getMediaItemAt(nextIndex)
            val nextMime = nextItem.localConfiguration?.mimeType
            if (stream.isHls && nextMime != stream.mimeType) {
                val position = player.currentPosition
                val wasPlaying = player.playWhenReady
                player.setMediaItems(
                    mediaItems(
                        queue = state.queue,
                        currentIndex = currentIndex,
                        currentMime = currentMime,
                        nextIndex = nextIndex,
                        nextMime = stream.mimeType,
                    ),
                    currentIndex,
                    position,
                )
                player.prepare()
                if (wasPlaying) player.play()
            } else {
                applyMime(player, nextIndex, stream.mimeType)
            }
        }
    }

    private fun applyMime(player: Player, index: Int, mimeType: String?) {
        if (index !in 0 until player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        val desired = mimeType ?: TrackMediaItem.trackOf(item)?.let(::defaultMime) ?: return
        val current = item.localConfiguration?.mimeType
        if (current != desired) {
            player.replaceMediaItem(index, TrackMediaItem.withMimeType(item, desired))
        }
    }

    private fun ensurePlayerQueue(player: Player, queue: List<Track>, index: Int) {
        if (player.mediaItemCount == queue.size) return
        player.setMediaItems(mediaItems(queue), index, 0L)
        player.repeatMode = repeatMode()
    }

    /** Сохранённая очередь: после подключения кладём её в плеер без автоплея. */
    private fun restoreQueueFromDb() {
        scope.launch {
            val snapshot = runCatching { queueRepository.load() }.getOrNull() ?: return@launch
            if (snapshot.tracks.isEmpty()) return@launch
            if (_state.value.queue.isNotEmpty() || _state.value.currentTrack != null) return@launch
            val index = snapshot.currentIndex.coerceIn(0, snapshot.tracks.lastIndex)
            originalQueue = snapshot.tracks
            snapshot.tracks.forEach(playbackResolver::remember)
            _state.update {
                it.copy(
                    queue = snapshot.tracks,
                    currentIndex = index,
                    currentTrack = snapshot.tracks[index],
                    isPlaying = false,
                    isBuffering = false,
                    isLoading = false,
                )
            }
            val player = awaitPlayer() ?: return@launch
            if (player.mediaItemCount == 0) {
                val currentMime = playbackResolver
                    .resolve(snapshot.tracks[index])
                    .getOrNull()
                    ?.mimeType
                player.setMediaItems(
                    mediaItems(snapshot.tracks, index, currentMime),
                    index,
                    snapshot.positionMs,
                )
                player.repeatMode = repeatMode()
                preloadNext()
            }
        }
    }

    /**
     * После потери аудиофокуса (TikTok, звонок) возвращаемся к игре, если
     * пользователь вернулся в Facet в течение 15 минут и настройка включена.
     */
    suspend fun resumeAfterFocusLoss() {
        val pausedAt = audioFocusPausedAt ?: return
        if (System.currentTimeMillis() - pausedAt > FOCUS_RESUME_WINDOW_MS) {
            audioFocusPausedAt = null
            return
        }
        val enabled = runCatching {
            settingsRepository.settings.first().resumeAfterFocusLoss
        }.getOrDefault(true)
        if (!enabled) return
        val player = awaitPlayer() ?: return
        if (!player.isPlaying) player.play()
        audioFocusPausedAt = null
    }

    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val FOCUS_RESUME_WINDOW_MS = 15 * 60 * 1000L
        const val PLAYER_UNAVAILABLE_MESSAGE = "Не удалось подключиться к плееру"
    }
}

enum class QueueOrigin {
    MAIN,
    WAVE,
    WAVE_FROM_TRACK,
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val playbackSpeed: Float = 1.0f,
    val tuningHz: Int = 440,
    val variation: PlaybackVariation = PlaybackVariation.NORMAL,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val queueOrigin: QueueOrigin = QueueOrigin.MAIN,
    val error: String? = null,
)
