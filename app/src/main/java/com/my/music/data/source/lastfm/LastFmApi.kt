package com.my.music.data.source.lastfm

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Минимальный клиент Last.fm для поиска похожих артистов.
 */
interface LastFmApi {

    @GET("2.0/")
    suspend fun getSimilarArtists(
        @Query("method") method: String,
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
    ): LastFmSimilarArtistsResponse
}

@Serializable
data class LastFmSimilarArtistsResponse(
    val similarartists: LastFmSimilarArtists? = null,
)

@Serializable
data class LastFmSimilarArtists(
    val artist: List<LastFmArtist> = emptyList(),
)

@Serializable
data class LastFmArtist(
    val name: String = "",
)
