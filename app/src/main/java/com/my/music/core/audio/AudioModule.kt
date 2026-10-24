package com.my.music.core.audio

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль аудио-анализа.
 *
 * Сессия ONNX создаётся лениво: при первом реальном инференсе, а не при
 * старте приложения, чтобы не задерживать запуск и не грузить 16 МБ модели
 * в память без необходимости.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideAudioEmbeddingEngine(
        @ApplicationContext context: Context,
    ): AudioEmbeddingEngine = AudioEmbeddingEngine(
        OnnxAudioModelSession(context.assets),
    )
}
