package com.facet.music.data.repository

import com.facet.music.core.spotify.SpotifyAuthEvent
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.core.spotify.SpotifyPlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SpotifyAuthRepository {

    val authState: StateFlow<SpotifyAuthState>

    val events: Flow<SpotifyAuthEvent>

    fun isRedirect(uri: String): Boolean

    suspend fun startAuthorization(clientId: String): Result<String>

    suspend fun completeAuthorization(uri: String): Result<Unit>

    suspend fun accessToken(forceRefresh: Boolean = false): String?

    suspend fun fetchMyPlaylists(): Result<List<SpotifyPlaylistSummary>>

    suspend fun signOut()
}
