package com.facet.music.core.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.facet.music.MainActivity
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.network.MediaClient
import com.facet.music.core.widget.ACTION_WIDGET_LIKE
import com.facet.music.core.widget.FacetWidgetReceiver
import com.facet.music.core.widget.WidgetArtworkStore
import com.facet.music.core.widget.WidgetFormat
import com.facet.music.core.widget.WidgetWave
import com.facet.music.core.widget.WidgetWaveSpecStore
import com.facet.music.core.widget.waveRemoteViews
import com.facet.music.core.widget.WidgetSnapshotStore
import com.facet.music.core.widget.hasWidgets
import com.facet.music.core.widget.pushWidgetState
import com.facet.music.core.widget.WidgetSnapshot
import com.facet.music.data.model.Track
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.SettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject
    lateinit var playbackDataSourceFactory: PlaybackDataSourceFactory

    @Inject
    lateinit var loadErrorPolicy: PlaybackLoadErrorPolicy

    @Inject
    lateinit var queueRepository: QueueRepository

    @Inject
    lateinit var playbackResolver: PlaybackResolver

    @Inject
    lateinit var likedTracksRepository: LikedTracksRepository

    @Inject
    lateinit var trackLikeManager: TrackLikeManager

    @Inject
    lateinit var equalizerController: EqualizerController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var coordinator: PlaybackSessionCoordinator

    @Inject
    @MediaClient
    lateinit var mediaClient: OkHttpClient

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Volatile
    private var widgetSnapshot = WidgetSnapshot()
    private var widgetTicker: Job? = null
    private var widgetArtworkJob: Job? = null
    private var widgetLikedJob: Job? = null
    private var widgetLikedTrackId: String? = null
    private var widgetArtworkTrackId: String? = null
    @Volatile
    private var widgetTrack: Track? = null
    private var widgetThemeKey = ""

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var sessionActivity: PendingIntent

    private val widgetCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_WIDGET_LIKE) return
            handleWidgetLike()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        coordinator.start()
        widgetSnapshot = WidgetSnapshotStore.read(this)
        ContextCompat.registerReceiver(
            this,
            widgetCommandReceiver,
            IntentFilter(ACTION_WIDGET_LIKE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun handleWidgetLike() {
        scope.launch {
            val track = withContext(Dispatchers.Main.immediate) {
                exoPlayer?.let { TrackMediaItem.trackOf(it.currentMediaItem) }
            } ?: return@launch
            trackLikeManager.toggle(track)
            mediaSession?.let(::updateLikeButton)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        mediaSession?.let { return it }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(
            playbackDataSourceFactory.cacheDataSourceFactory,
        ).setLoadErrorHandlingPolicy(loadErrorPolicy)

        val audioSessionId = runCatching {
            (getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)
                ?.generateAudioSessionId()
        }.getOrNull()?.takeIf { it > 0 } ?: 0

        val silenceSkipping = androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor()

        val buffer = PlaybackAudioConfig.bufferProfile(
            settingsRepository.snapshot?.audioBufferProfile ?: "MEDIUM",
        )
        val player = ExoPlayer.Builder(this)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        buffer.minBufferMs,
                        buffer.maxBufferMs,
                        buffer.playBufferMs,
                        buffer.rebufferMs,
                    )
                    .build(),
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(FacetRenderersFactory(this, silenceSkipping))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        scope.launch {
            settingsRepository.settings.collect { settings ->
                silenceSkipping.setEnabled(settings.skipSilenceEnabled)
                withContext(Dispatchers.Main) {
                    player.skipSilenceEnabled = settings.skipSilenceEnabled
                }
                val themeKey = listOf(
                    settings.themeMode.name,
                    settings.amoledBlack,
                    settings.customAccentColor,
                    settings.accentEnabled,
                    settings.paletteMode,
                    settings.shapeStyle,
                    settings.serifDisplay,
                ).joinToString("|")
                if (widgetThemeKey.isNotEmpty() && themeKey != widgetThemeKey && widgetSnapshot.hasTrack) {
                    scope.launch { runCatching { pushWidgetState(this@MusicService) } }
                }
                widgetThemeKey = themeKey
            }
        }

        if (audioSessionId > 0) {
            runCatching { player.setAudioSessionId(audioSessionId) }
            AudioSessionBridge.set(audioSessionId)
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                mediaSession?.let(::updateLikeButton)
                syncWidget(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncWidget(player)
                updateFadeLoop(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                syncWidget(player)
            }
        })

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> true
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToPrevious() {
                if (player.currentPosition > 3000L || !player.hasPreviousMediaItem()) {
                    player.seekTo(0L)
                } else {
                    player.seekToPreviousMediaItem()
                }
            }
        }

        exoPlayer = player
        val session = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setBitmapLoader(ArtworkBitmapLoader(this))
            .setMediaButtonPreferences(listOf(PlaybackSessionCommands.likeButton(false)))
            .build()
        mediaSession = session
        updateLikeButton(session)
        return session
    }

    private fun updateLikeButton(session: MediaSession) {
        scope.launch {
            val track = withContext(Dispatchers.Main.immediate) {
                TrackMediaItem.trackOf(session.player.currentMediaItem)
            } ?: return@launch
            val liked = runCatching {
                likedTracksRepository.isLiked(track.id).first()
            }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    session.setMediaButtonPreferences(
                        listOf(PlaybackSessionCommands.likeButton(liked)),
                    )
                }
            }
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val trusted = controller.packageName == packageName ||
                controller.isTrusted ||
                session.isMediaNotificationController(controller) ||
                session.isAutomotiveController(controller) ||
                session.isAutoCompanionController(controller)
            return if (trusted) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(PlaybackSessionCommands.LIKE)
                    .build()
                MediaSession.ConnectionResult.accept(
                    commands,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                )
            } else {
                MediaSession.ConnectionResult.accept(
                    SessionCommands.EMPTY,
                    SAFE_CONTROLLER_COMMANDS,
                )
            }
        }

        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val snapshot = runCatching { queueRepository.load() }.getOrNull()
                if (snapshot == null || snapshot.tracks.isEmpty()) {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L),
                    )
                    return@launch
                }
                val index = snapshot.currentIndex.coerceIn(0, snapshot.tracks.lastIndex)
                snapshot.tracks.forEach(playbackResolver::remember)
                val current = snapshot.tracks[index]
                val currentMime = runCatching {
                    playbackResolver.resolve(current).getOrNull()?.mimeType
                }.getOrNull()
                val items = snapshot.tracks.mapIndexed { itemIndex, track ->
                    TrackMediaItem.create(
                        track = track,
                        mimeType = if (itemIndex == index && currentMime != null) {
                            currentMime
                        } else {
                            TrackMediaItem.defaultMimeType(track)
                        },
                    )
                }
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        items,
                        index,
                        snapshot.positionMs,
                    ),
                )
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand != PlaybackSessionCommands.LIKE) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            scope.launch {
                val track = withContext(Dispatchers.Main.immediate) {
                    TrackMediaItem.trackOf(session.player.currentMediaItem)
                }
                if (track != null) {
                    trackLikeManager.toggle(track)
                    updateLikeButton(session)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mediaSession?.player?.isPlaying != true) {
            pauseAllPlayersAndStopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private var fadeJob: Job? = null

    private fun updateFadeLoop(player: Player) {
        fadeJob?.cancel()
        if (!player.isPlaying) {
            runCatching { player.volume = 1f }
            return
        }
        fadeJob = scope.launch(Dispatchers.Main) {
            var applied = -1f
            while (isActive && player.isPlaying) {
                val fadeMs = settingsRepository.snapshot?.fadeMs ?: 0
                val duration = player.duration
                val volume = PlaybackAudioConfig.fadeVolume(
                    positionMs = player.currentPosition,
                    durationMs = if (duration == C.TIME_UNSET) 0L else duration,
                    fadeMs = fadeMs,
                    hasNext = player.hasNextMediaItem(),
                )
                if (kotlin.math.abs(volume - applied) > 0.01f) {
                    applied = volume
                    runCatching { player.volume = volume }
                }
                delay(50L)
            }
            runCatching { player.volume = 1f }
        }
    }

    private fun syncWidget(player: Player) {
        val item = player.currentMediaItem
        val track = TrackMediaItem.trackOf(item)
        val trackChanged = track?.id != widgetSnapshot.trackId
        pushWidget(
            WidgetSnapshot(
                trackId = track?.id.orEmpty(),
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artworkUrl = track?.artworkUrl.orEmpty(),
                artToken = if (trackChanged) "" else widgetSnapshot.artToken,
                isPlaying = player.isPlaying,
                isLiked = if (trackChanged) false else widgetSnapshot.isLiked,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = playerDuration(player, track),
            ),
        )
        widgetTrack = track
        if (track != null) {
            observeWidgetLiked(track)
            if (trackChanged) widgetArtworkTrackId = null
            if (trackChanged || !widgetSnapshot.hasArtwork) refreshWidgetArtwork(track)
        }
        updateWidgetTicker(player)
    }

    private fun playerDuration(player: Player, track: Track?): Long {
        val current = player.duration
        return if (current != C.TIME_UNSET && current > 0L) {
            current
        } else {
            track?.durationMs ?: 0L
        }
    }

    private fun pushWidget(snapshot: WidgetSnapshot) {
        widgetSnapshot = snapshot
        WidgetSnapshotStore.write(this, snapshot)
        scope.launch { runCatching { pushWidgetState(this@MusicService) } }
    }

    private fun observeWidgetLiked(track: Track) {
        if (widgetLikedTrackId == track.id) return
        widgetLikedTrackId = track.id
        widgetLikedJob?.cancel()
        widgetLikedJob = scope.launch {
            likedTracksRepository.isLiked(track.id).collect { liked ->
                val current = widgetSnapshot
                if (current.trackId == track.id && current.isLiked != liked) {
                    pushWidget(current.copy(isLiked = liked))
                }
            }
        }
    }

    private fun refreshWidgetArtwork(track: Track) {
        if (widgetArtworkTrackId == track.id) return
        val url = track.artworkUrl?.takeIf { it.isNotBlank() } ?: return
        widgetArtworkTrackId = track.id
        widgetArtworkJob?.cancel()
        widgetArtworkJob = scope.launch {
            if (!hasWidgets(this@MusicService)) {
                widgetArtworkTrackId = null
                return@launch
            }
            val changed = runCatching {
                WidgetArtworkStore.refresh(
                    context = this@MusicService,
                    trackId = track.id,
                    artworkUrl = url,
                    client = mediaClient,
                )
            }.getOrDefault(false)
            if (changed && widgetSnapshot.trackId == track.id) {
                pushWidget(widgetSnapshot.copy(artToken = track.id))
            }
        }
    }

    private fun updateWidgetTicker(player: Player) {
        if (!player.isPlaying) {
            widgetTicker?.cancel()
            widgetTicker = null
            return
        }
        if (widgetTicker?.isActive == true) return
        widgetTicker = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(WIDGET_TICK_MS)
                if (!hasWidgets(this@MusicService)) continue
                if (!isScreenInteractive()) continue
                val state = withContext(Dispatchers.Main.immediate) {
                    if (!player.isPlaying) {
                        null
                    } else {
                        player.currentPosition.coerceAtLeast(0L) to playerDuration(player, null)
                    }
                } ?: break
                val current = widgetSnapshot
                val duration = if (state.second > 0L) state.second else current.durationMs
                updateWidgetWave(state.first, duration)
                ticks++
                if (ticks % FULL_SYNC_TICKS == 0) {
                    val track = widgetTrack
                    if (track != null && !current.hasArtwork) refreshWidgetArtwork(track)
                    pushWidget(
                        current.copy(
                            positionMs = state.first,
                            durationMs = duration,
                            isPlaying = true,
                        ),
                    )
                }
            }
        }
    }

    private fun isScreenInteractive(): Boolean =
        getSystemService(android.os.PowerManager::class.java)?.isInteractive ?: true

    private fun updateWidgetWave(positionMs: Long, durationMs: Long) {
        val manager = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            android.content.ComponentName(this, FacetWidgetReceiver::class.java),
        )
        if (ids.isEmpty()) return
        val progress = WidgetFormat.fraction(positionMs, durationMs)
        val specs = WidgetWaveSpecStore.read(this)
        ids.forEach { id ->
            val spec = specs[id] ?: return@forEach
            val bitmap = WidgetWave.render(
                context = this,
                width = spec.widthDp.dp,
                height = spec.heightDp.dp,
                progress = progress,
                accent = Color(spec.accent),
                track = Color(spec.track),
            ) ?: return@forEach
            runCatching {
                manager.partiallyUpdateAppWidget(id, waveRemoteViews(this, bitmap))
            }
        }
    }

    override fun onDestroy() {
        widgetTicker?.cancel()
        widgetTicker = null
        widgetArtworkJob?.cancel()
        widgetArtworkJob = null
        widgetLikedJob?.cancel()
        widgetLikedJob = null
        runCatching { unregisterReceiver(widgetCommandReceiver) }
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val WIDGET_TICK_MS = 1_000L
        const val FULL_SYNC_TICKS = 10

        val SAFE_CONTROLLER_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            )
            .build()
    }
}
