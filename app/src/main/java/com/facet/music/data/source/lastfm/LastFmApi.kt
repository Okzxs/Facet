package com.facet.music.data.source.lastfm

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Минимальный клиент Last.fm: похожие артисты, топ-треки артиста и тега,
 * теги артиста. Ключ передаётся параметром, формат — всегда JSON.
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

    @GET("2.0/")
    suspend fun getArtistTopTracks(
        @Query("method") method: String,
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int,
    ): LastFmTopTracksResponse

    @GET("2.0/")
    suspend fun getTagTopTracks(
        @Query("method") method: String,
        @Query("tag") tag: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int,
    ): LastFmTopTracksResponse

    @GET("2.0/")
    suspend fun getTopArtists(
        @Query("method") method: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
    ): LastFmTopArtistsResponse

    @GET("2.0/")
    suspend fun getArtistTopTags(
        @Query("method") method: String,
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
    ): LastFmTopTagsResponse
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

@Serializable
data class LastFmTopTracksResponse(
    val toptracks: LastFmTopTracks? = null,
)

@Serializable
data class LastFmTopTracks(
    val track: List<LastFmTrackDto> = emptyList(),
)

@Serializable
data class LastFmTrackDto(
    val name: String = "",
    val duration: Long = 0L,
    val listeners: Long = 0L,
    val artist: LastFmTrackArtist? = null,
)

@Serializable
data class LastFmTrackArtist(
    val name: String = "",
)

@Serializable
data class LastFmTopTagsResponse(
    val toptags: LastFmTopTags? = null,
)

@Serializable
data class LastFmTopTags(
    val tag: List<LastFmTag> = emptyList(),
)

@Serializable
data class LastFmTag(
    val name: String = "",
)

@Serializable
data class LastFmTopArtistsResponse(
    val artists: LastFmTopArtists? = null,
)

@Serializable
data class LastFmTopArtists(
    val artist: List<LastFmArtist> = emptyList(),
)
