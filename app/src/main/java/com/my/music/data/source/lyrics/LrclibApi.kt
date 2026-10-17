package com.my.music.data.source.lyrics

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class LrclibResponse(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

interface LrclibApi {

    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") durationSec: Long? = null,
    ): Response<LrclibResponse>

    @GET("search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("q") query: String? = null,
    ): Response<List<LrclibResponse>>
}
