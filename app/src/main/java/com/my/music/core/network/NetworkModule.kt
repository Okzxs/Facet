package com.my.music.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MyMusicApp/1.0 (https://github.com/souler/mymusic)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit для LRCLIB (тексты песен).
     * SoundCloud теперь обрабатывается через NewPipe — Retrofit для него не нужен.
     */
    @Provides
    @Singleton
    fun provideLrclibRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/api/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideLrclibApi(retrofit: Retrofit): com.my.music.data.source.lyrics.LrclibApi =
        retrofit.create(com.my.music.data.source.lyrics.LrclibApi::class.java)

    /**
     * Отдельный Retrofit для Last.fm: у сервиса другой базовый URL.
     */
    @Provides
    @Singleton
    fun provideLastFmApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.my.music.data.source.lastfm.LastFmApi = Retrofit.Builder()
        .baseUrl("https://ws.audioscrobbler.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(com.my.music.data.source.lastfm.LastFmApi::class.java)
}
