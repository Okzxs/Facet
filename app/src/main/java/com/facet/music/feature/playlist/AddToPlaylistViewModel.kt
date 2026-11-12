package com.facet.music.feature.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Playlist
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _added = Channel<String>(Channel.BUFFERED)
    val added: Flow<String> = _added.receiveAsFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun addTrack(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            runCatching { repository.addTrack(playlist.id, track) }
                .rethrowCancellation()
                .onSuccess {
                    _failed.value = false
                    _added.trySend(playlist.name)
                }
                .onFailure { error ->
                    Log.e(TAG, "Не удалось добавить трек в плейлист", error)
                    _failed.value = true
                }
        }
    }

    fun createAndAdd(track: Track, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val playlistId = repository.createPlaylist(trimmed)
                repository.addTrack(playlistId, track)
            }
                .rethrowCancellation()
                .onSuccess {
                    _failed.value = false
                    _added.trySend(trimmed)
                }
                .onFailure { error ->
                    Log.e(TAG, "Не удалось создать плейлист", error)
                    _failed.value = true
                }
        }
    }

    private companion object {
        const val TAG = "AddToPlaylistViewModel"
    }
}
