package com.my.music.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль для ExoPlayer и PlayerController.
 * Оба — синглтоны уровня приложения, живут до его закрытия.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // пауза при отключении наушников
            .build()
    }

    @Provides
    @Singleton
    fun providePlayerController(
        exoPlayer: ExoPlayer,
        soundCloudRepository: com.my.music.data.repository.SoundCloudRepository,
        playHistoryRepository: com.my.music.data.repository.PlayHistoryRepository,
    ): PlayerController =
        PlayerController(exoPlayer, soundCloudRepository, playHistoryRepository)
}

