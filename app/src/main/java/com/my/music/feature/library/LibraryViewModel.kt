package com.my.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.LikedTracksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    downloadRepository: DownloadRepository,
) : ViewModel() {

    val likedTracks: StateFlow<List<Track>> = likedTracksRepository.getLikedTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** Идентификаторы скачанных треков — для бейджей «офлайн». */
    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    fun removeLike(track: Track) {
        viewModelScope.launch {
            likedTracksRepository.toggleLike(track)
        }
    }
}
