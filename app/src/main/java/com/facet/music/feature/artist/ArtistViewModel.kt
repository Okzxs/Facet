package com.facet.music.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlayerController
import com.facet.music.data.model.Track
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.data.source.external.DeezerAlbum
import com.facet.music.data.source.external.DeezerArtistRef
import com.facet.music.data.source.external.DeezerProvider
import com.facet.music.navigation.ARTIST_NAME_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экрана артиста.
 *
 * @param name имя из маршрута (показывается сразу, ещё до загрузки);
 * @param pictureUrl аватар из Deezer (null — рисуем градиентную заглушку);
 * @param fans число слушателей Deezer, 0 — статистики нет;
 * @param albumCount число альбомов Deezer, 0 — статистики нет;
 * @param tracks популярные треки в порядке Deezer либо YouTube-фолбэка;
 * @param albums альбомы для карусели (пусто — секция скрыта);
 * @param related похожие артисты (пусто — секция скрыта);
 * @param isLoading идёт ли загрузка;
 * @param failed ни Deezer, ни YouTube не дали треков — показываем ошибку с повтором.
 */
data class ArtistUiState(
    val name: String = "",
    val pictureUrl: String? = null,
    val fans: Int = 0,
    val albumCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val albums: List<DeezerAlbum> = emptyList(),
    val related: List<DeezerArtistRef> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Экран артиста: шапка с аватаром и статистикой, популярные треки, альбомы
 * и похожие артисты.
 *
 * Всё, кроме фолбэка на YouTube, берётся из Deezer: публичный API без ключей
 * отдаёт и топ артиста, и альбомы, и связанных исполнителей. Статистика и
 * альбомы не обязательны — при пустом ответе секции просто скрываются.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deezerProvider: DeezerProvider,
    private val youTubeRepository: YouTubeRepository,
    playerController: PlayerController,
) : ViewModel() {

    /** Имя артиста приходит аргументом маршрута и уже раскодировано навигацией. */
    val artistName: String = savedStateHandle.get<String>(ARTIST_NAME_ARG)?.trim().orEmpty()

    private val _uiState = MutableStateFlow(ArtistUiState(name = artistName))
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    /** Id играющего трека — его строка подсвечивается и получает эквалайзер. */
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

    /** Запрос на воспроизведение альбома: экран сам зовёт onTrackClick. */
    private val _playQueue = MutableSharedFlow<List<Track>>(extraBufferCapacity = 1)
    val playQueue: SharedFlow<List<Track>> = _playQueue.asSharedFlow()

    /** Альбом не загрузился — UI показывает Snackbar. */
    private val _albumError = MutableStateFlow(false)
    val albumError: StateFlow<Boolean> = _albumError.asStateFlow()

    init {
        loadArtist()
    }

    /** Повторная загрузка для кнопки «Повторить». */
    fun retry() {
        loadArtist()
    }

    /** Воспроизведение альбома целиком: треки приходят одним запросом. */
    fun playAlbum(albumId: Long) {
        viewModelScope.launch {
            val tracks = deezerProvider.fetchAlbumTracks(albumId).getOrNull().orEmpty()
            if (tracks.isEmpty()) {
                _albumError.value = true
            } else {
                _playQueue.emit(tracks)
            }
        }
    }

    fun consumeAlbumError() {
        _albumError.value = false
    }

    private fun loadArtist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, failed = false) }

            // Один поиск артиста даёт id для топа, альбомов и похожих.
            val artist = if (artistName.isBlank()) {
                null
            } else {
                deezerProvider.fetchArtist(artistName).getOrNull()
            }

            val deezerTracks = when {
                artist != null -> deezerProvider.fetchArtistTopTracks(artist.id).getOrNull()
                artistName.isNotBlank() -> deezerProvider.fetchArtistTopTracks(artistName).getOrNull()
                else -> null
            }.orEmpty()

            // У части артистов Deezer отдаёт в топ один трек — дополняем поиском
            // по `artist:"имя"`, иначе секция выглядит обрезанной.
            val filledTracks = if (deezerTracks.size < MIN_TOP_TRACKS && artistName.isNotBlank()) {
                val extra = deezerProvider.searchArtistTracks(artistName).getOrNull().orEmpty()
                (deezerTracks + extra)
                    .distinctBy { it.id }
                    .take(ARTIST_TRACK_LIMIT)
            } else {
                deezerTracks
            }

            // Deezer не нашёл артиста — ищем официальные аудио на YouTube.
            val tracks = filledTracks.ifEmpty { youTubeFallback() }

            val albums = artist?.let {
                deezerProvider.fetchArtistAlbums(it.id).getOrNull()
            }.orEmpty()

            val related = artist?.let {
                deezerProvider.fetchRelatedArtists(it.id).getOrNull()
            }.orEmpty()

            _uiState.update { current ->
                current.copy(
                    // Имя из Deezer точнее введённого вручную: там оригинальная
                    // раскладка. Пустое — оставляем имя из маршрута.
                    name = artist?.name ?: current.name,
                    pictureUrl = artist?.pictureUrl,
                    fans = artist?.fans ?: 0,
                    albumCount = artist?.albums ?: 0,
                    tracks = tracks,
                    albums = albums,
                    related = related,
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

        /** Меньше этого числа треков в топе Deezer считаем «пустым» и дополняем. */
        const val MIN_TOP_TRACKS = 5

        /** Сколько треков артиста показываем всего. */
        const val ARTIST_TRACK_LIMIT = 20
    }
}
