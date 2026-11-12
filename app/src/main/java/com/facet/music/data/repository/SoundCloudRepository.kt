package com.facet.music.data.repository

import com.facet.music.core.download.DownloadCapability
import com.facet.music.data.model.Artist
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage

interface SoundCloudRepository {

    val downloadCapability: DownloadCapability

    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    suspend fun searchPage(
        query: String,
        limit: Int = 20,
        nextToken: String? = null,
    ): Result<TrackPage>

    suspend fun searchArtists(query: String, limit: Int = 10): Result<List<Artist>>

    suspend fun searchUserTracks(userUrl: String, limit: Int = 10): Result<List<Track>>

    suspend fun getRelated(trackUrl: String, limit: Int = 30): Result<List<Track>>

    suspend fun resolveStreamUrl(
        transcodingUrl: String,
        requireProgressive: Boolean = false,
        numericId: String? = null,
    ): Result<AudioStreamResult>
}
