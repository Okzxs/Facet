package com.facet.music.feature.playlist

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlayerController
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.Playlist
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlaylistRepository
import com.facet.music.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Экран плейлиста: имя, треки и удаление трека.
 *
 * Аргумент маршрута приходит через [SavedStateHandle] — экран открывается
 * из медиатеки и переживает пересоздание процесса без потери id.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
    playerController: PlayerController,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle[Screen.Playlist.ARG_ID]) {
        "Маршрут плейлиста открыт без аргумента ${Screen.Playlist.ARG_ID}"
    }

    val playlist: StateFlow<Playlist?> = repository.observePlaylist(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val tracks: StateFlow<List<Track>> = repository.observeTracks(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Id играющего трека — строка подсвечивается и показывает эквалайзер. */
    val currentTrackId: StateFlow<String?> = playerController.state
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val isPlaying: StateFlow<Boolean> = playerController.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Ошибка удаления — UI переводит флаг в локализованный Snackbar. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Плейлист удалён: экран закрывается, возвращаться в него некуда. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            runCatching { repository.removeTrack(playlistId, trackId) }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(TAG, "Не удалось удалить трек из плейлиста", error)
                    _failed.value = true
                }
        }
    }

    /** Удаление плейлиста вместе с треками (каскад внешнего ключа). */
    fun deletePlaylist() {
        viewModelScope.launch {
            runCatching { repository.deletePlaylist(playlistId) }
                .rethrowCancellation()
                .onSuccess { _deleted.value = true }
                .onFailure { error ->
                    Log.e(TAG, "Не удалось удалить плейлист", error)
                    _failed.value = true
                }
        }
    }

    fun consumeFailure() {
        _failed.value = false
    }

    private companion object {
        const val TAG = "PlaylistViewModel"
    }
}
