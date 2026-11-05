package com.facet.music.core.network

import com.facet.music.BuildConfig
import com.facet.music.data.source.lastfm.LastFmApi
import com.facet.music.data.source.lyrics.LrclibApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
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

    /**
     * Общая основа: пул соединений и диспетчер делятся между API- и
     * медиа-клиентами через newBuilder(), поэтому сокеты не удваиваются.
     */
    @Provides
    @Singleton
    @Named(BASE_CLIENT)
    fun provideBaseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
        .dispatcher(Dispatcher())
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(userAgentInterceptor())
        .build()

    /**
     * JSON-API: короткие таймауты, Accept: application/json, повторы для
     * идемпотентных GET. Заголовок Accept ставится только здесь: NewPipe
     * и медиа-запросы не должны получать JSON вместо HTML/аудио.
     */
    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(@Named(BASE_CLIENT) base: OkHttpClient): OkHttpClient {
        val builder = base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(acceptJsonInterceptor())
            .addInterceptor(RetryInterceptor())
        if (BuildConfig.DEBUG) builder.addInterceptor(loggingInterceptor())
        return builder.build()
    }

    /**
     * Медиа-клиент: аудиопоток и HLS-сегменты читаются дольше, общего
     * дедлайна нет. Скачивание использует этот же клиент через EntryPoint.
     */
    @Provides
    @Singleton
    @MediaClient
    fun provideMediaClient(@Named(BASE_CLIENT) base: OkHttpClient): OkHttpClient {
        return base.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideLrclibRetrofit(
        @ApiClient okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/api/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideLrclibApi(retrofit: Retrofit): LrclibApi = retrofit.create(LrclibApi::class.java)

    @Provides
    @Singleton
    fun provideLastFmApi(
        @ApiClient okHttpClient: OkHttpClient,
        json: Json,
    ): LastFmApi = Retrofit.Builder()
        .baseUrl("https://ws.audioscrobbler.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LastFmApi::class.java)

    private fun userAgentInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", FALLBACK_USER_AGENT)
        }
        chain.proceed(builder.build())
    }

    private fun acceptJsonInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        chain.proceed(builder.build())
    }

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private const val BASE_CLIENT = "base"

    private const val MAX_IDLE_CONNECTIONS = 8

    private const val KEEP_ALIVE_MINUTES = 5L

    private const val FALLBACK_USER_AGENT =
        "Facet/1.0 (https://github.com/Okzxs/my-music-app)"
}
