package com.facet.music.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.facet.music.BuildConfig
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
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                // Подставляем заголовки, только если их не задал вызывающий код:
                // header() перезаписывает значение, и NewPipe терял свой браузерный
                // User-Agent — SoundCloud/YouTube отвечают на небраузерный UA
                // блокировкой (403/ReCaptcha).
                if (original.header("User-Agent") == null) {
                    builder.header("User-Agent", FALLBACK_USER_AGENT)
                }
                if (original.header("Accept") == null) {
                    builder.header("Accept", "application/json")
                }
                chain.proceed(builder.build())
            }

        // BASIC-логи содержат подписанные URL и токены — только для debug-сборки.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }

        return builder
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
    fun provideLrclibApi(retrofit: Retrofit): com.facet.music.data.source.lyrics.LrclibApi =
        retrofit.create(com.facet.music.data.source.lyrics.LrclibApi::class.java)

    /**
     * Отдельный Retrofit для Last.fm: у сервиса другой базовый URL.
     */
    @Provides
    @Singleton
    fun provideLastFmApi(
        okHttpClient: OkHttpClient,
        json: Json,
    ): com.facet.music.data.source.lastfm.LastFmApi = Retrofit.Builder()
        .baseUrl("https://ws.audioscrobbler.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(com.facet.music.data.source.lastfm.LastFmApi::class.java)

    /** Резервный User-Agent для запросов, которые его не задали сами. */
    private const val FALLBACK_USER_AGENT =
        "Facet/1.0 (https://github.com/Okzxs/my-music-app)"
}
