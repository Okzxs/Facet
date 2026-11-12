package com.facet.music.data.repository

import com.facet.music.data.model.Playlist
import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    fun observeTracks(playlistId: Long): Flow<List<Track>>

    suspend fun createPlaylist(name: String): Long

    suspend fun addTrack(playlistId: Long, track: Track)

    suspend fun removeTrack(playlistId: Long, trackId: String)

    suspend fun deletePlaylist(playlistId: Long)

    suspend fun rename(playlistId: Long, name: String)
}
