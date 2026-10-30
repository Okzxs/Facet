package com.my.music.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.model.Track
import com.my.music.data.repository.YouTubeRepository
import com.my.music.data.source.external.DeezerProvider
import com.my.music.navigation.ARTIST_NAME_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экрана артиста.
 *
 * @param name имя из маршрута (показывается сразу, ещё до загрузки);
 * @param tracks популярные треки в порядке Deezer либо YouTube-фолбэка;
 * @param isLoading идёт ли загрузка;
 * @param failed ни Deezer, ни YouTube не дали треков — показываем ошибку с повтором.
 */
data class ArtistUiState(
    val name: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Экран артиста: топ-треки Deezer (без авторизации, 30-секундные превью),
 * при пустом ответе — поиск YouTube `"<артист> official audio"`.
 *
 * Deezer выбран первым: он отдаёт популярные треки именно артиста, а не
 * случайные видео, и не требует ключей.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deezerProvider: DeezerProvider,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    /** Имя артиста приходит аргументом маршрута и уже раскодировано навигацией. */
    val artistName: String = savedStateHandle.get<String>(ARTIST_NAME_ARG)?.trim().orEmpty()

    private val _uiState = MutableStateFlow(ArtistUiState(name = artistName))
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
    }

    /** Повторная загрузка для кнопки «Повторить». */
    fun retry() {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, failed = false) }

            val deezerTracks = if (artistName.isBlank()) {
                emptyList()
            } else {
                deezerProvider.fetchArtistTopTracks(artistName)
                    .getOrNull()
                    .orEmpty()
            }

            // Deezer не нашёл артиста — ищем официальные аудио на YouTube.
            val tracks = deezerTracks.ifEmpty { youTubeFallback() }

            _uiState.update {
                it.copy(
                    tracks = tracks,
                    isLoading = false,
                    failed = tracks.isEmpty(),
                )
            }
        }
    }

    private suspend fun youTubeFallback(): List<Track> =
        youTubeRepository
            .search(
                query = "$artistName $YOUTUBE_FALLBACK_SUFFIX",
                limit = FALLBACK_LIMIT,
            )
            .getOrNull()
            .orEmpty()

    private companion object {
        const val YOUTUBE_FALLBACK_SUFFIX = "official audio"
        const val FALLBACK_LIMIT = 20
    }
}
