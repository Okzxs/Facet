package com.my.music.core.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Номер аудиосессии ExoPlayer для декоративного визуализатора.
 *
 * ExoPlayer живёт в [MusicService] (тот же процесс, что и UI), поэтому сервис
 * публикует номер сессии, а экран плеера читает его без IPC. Пока сессия
 * не создана, значение равно 0 — вызывающая сторона сама решает, ждать или
 * уходить в ambient-фолбэк.
 */
object PlaybackAudioSession {

    @Volatile
    var audioSessionId: Int = 0
        private set

    fun attach(player: ExoPlayer) {
        publish(player)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = publish(player)

            override fun onIsPlayingChanged(isPlaying: Boolean) = publish(player)
        })
    }

    private fun publish(player: ExoPlayer) {
        val id = runCatching { player.audioSessionId }.getOrDefault(0)
        if (id != 0) audioSessionId = id
    }
}
