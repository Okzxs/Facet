package com.my.music.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.my.music.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground-сервис для фонового воспроизведения.
 * Media3 автоматически показывает MediaStyle-уведомление с кнопками управления.
 * Кнопки Prev/Next перенаправляются в очередь PlayerController через ForwardingPlayer.
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: androidx.media3.exoplayer.ExoPlayer

    @Inject
    lateinit var playerController: PlayerController

    private var mediaSession: MediaSession? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        // PendingIntent для открытия MainActivity при нажатии на уведомление
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
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

            override fun seekToNext() {
                playerController.playNext()
            }

            override fun seekToNextMediaItem() {
                playerController.playNext()
            }

            override fun seekToPrevious() {
                playerController.playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playerController.playPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            exoPlayer.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
