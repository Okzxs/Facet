package com.my.music.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.download.DownloadManager
import com.my.music.core.download.DownloadMessages
import com.my.music.core.download.DownloadState
import com.my.music.core.player.PlayerController
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.LikedTracksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    playerController: PlayerController,
) : ViewModel() {

    val likedTracks: StateFlow<List<Track>> = likedTracksRepository.getLikedTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** Id играющего трека — строка в списке подсвечивается и показывает эквалайзер. */
    val currentTrackId: StateFlow<String?> = playerController.state
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    /** Идентификаторы скачанных треков — для бейджей «офлайн». */
    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    /** Состояния кнопок скачивания в списке лайков. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val downloadStates: StateFlow<Map<String, DownloadState>> = likedTracks
        .map { tracks -> tracks.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> downloadManager.observeStates(ids) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /** Ошибка БД при снятии лайка — показывается Snackbar'ом вместо краша. */
    private val _likeError = MutableStateFlow<String?>(null)
    val likeError: StateFlow<String?> = _likeError.asStateFlow()

    fun removeLike(track: Track) {
        viewModelScope.launch {
            runCatching { likedTracksRepository.toggleLike(track) }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(TAG, "Не удалось снять лайк", error)
                    _likeError.value = LIKE_FAILED_MESSAGE
                }
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            downloadManager.download(track).onFailure {
                _downloadError.value = DownloadMessages.SOURCE_NOT_FOUND
            }
        }
    }

    fun consumeDownloadError() {
        _downloadError.value = null
    }

    fun consumeLikeError() {
        _likeError.value = null
    }

    private companion object {
        const val TAG = "LibraryViewModel"
        const val LIKE_FAILED_MESSAGE = "Не удалось обновить лайк: база данных недоступна"
    }
}
