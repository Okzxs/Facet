package com.facet.music.data.repository

import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPage
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistProfile
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {

    suspend fun artist(source: Source, url: String): Result<ArtistPage>

    suspend fun cached(source: Source, url: String): ArtistPage?

    suspend fun store(page: ArtistPage)

    suspend fun popularTracks(source: Source, url: String, limit: Int = 10): Result<List<Track>>

    suspend fun releases(source: Source, url: String): Result<List<ArtistRelease>>

    suspend fun allTracks(
        source: Source,
        url: String,
        nextToken: String? = null,
        limit: Int = 30,
    ): Result<TrackPage>

    suspend fun releaseTracks(source: Source, url: String): Result<List<Track>>

    suspend fun playlists(source: Source, url: String): Result<List<ArtistPlaylist>>

    suspend fun related(source: Source, url: String, name: String): Result<List<Artist>>

    suspend fun resolveByName(name: String): Result<Artist?>

    fun observeFollowed(): Flow<Set<String>>

    suspend fun setFollowed(profile: ArtistProfile, followed: Boolean)
}
