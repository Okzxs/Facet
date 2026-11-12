package com.facet.music.data.repository

import com.facet.music.core.download.DownloadCapability
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage

interface YouTubeRepository {

    val downloadCapability: DownloadCapability

    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    suspend fun searchPage(
        query: String,
        limit: Int = 20,
        nextToken: String? = null,
    ): Result<TrackPage>

    suspend fun searchArtists(query: String, limit: Int = 10): Result<List<Artist>>

    suspend fun searchMusicSongs(query: String, limit: Int = 10): Result<List<Track>>

    suspend fun searchMusicAlbums(query: String, limit: Int = 20): Result<List<ArtistRelease>>

    suspend fun searchMusicPlaylists(query: String, limit: Int = 20): Result<List<ArtistPlaylist>>

    suspend fun getRelated(trackUrl: String, limit: Int = 30): Result<List<Track>>

    suspend fun resolveStreamUrl(videoUrl: String): Result<AudioStreamResult>
}
