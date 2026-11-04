package com.facet.music.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.facet.music.MainActivity
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.QueueRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground-сервис воспроизведения. ExoPlayer владеет очередью, поэтому
 * переходы между треками и повтор идут без stop()/пересоздания уведомления.
 */
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
    lateinit var coordinator: PlaybackSessionCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var sessionActivity: PendingIntent

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

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                mediaSession?.let(::updateLikeButton)
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

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
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
