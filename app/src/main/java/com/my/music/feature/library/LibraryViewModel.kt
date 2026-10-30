package com.my.music.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.core.download.DownloadManager
import com.my.music.core.download.DownloadMessages
import com.my.music.core.download.DownloadState
import com.my.music.core.import.ImportError
import com.my.music.core.import.ImportedPlaylist
import com.my.music.core.player.PlayerController
import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Playlist
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlaylistRepository
import com.my.music.data.source.imports.PlaylistImportResult
import com.my.music.data.source.imports.PlaylistImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/**
 * Состояние импорта по ссылке: диалог ссылки → загрузка → превью → сохранение.
 * Ошибки живут отдельным потоком, чтобы Snackbar не сбивал диалог превью.
 */
sealed interface PlaylistImportState {
    data object Idle : PlaylistImportState
    data object Loading : PlaylistImportState
    data class Preview(val playlist: ImportedPlaylist) : PlaylistImportState
    data object Saving : PlaylistImportState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val playlistRepository: PlaylistRepository,
    private val playlistImportService: PlaylistImportService,
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

    /** Играет ли плеер сейчас: на паузе эквалайзер заменяется точками. */
    val isPlaying: StateFlow<Boolean> = playerController.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    /** Идентификаторы скачанных треков — для бейджей «офлайн». */
    val downloadedIds: StateFlow<Set<String>> = downloadRepository.observeDownloadedIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    /** Сами скачанные треки — вкладка «Скачанное» в медиатеке. */
    val downloadedTracks: StateFlow<List<DownloadedTrack>> =
        downloadRepository.observeDownloadedTracks()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /** Плейлисты со счётчиками — третья вкладка медиатеки. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
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

    /** Ошибка удаления плейлиста — отдельный поток, чтобы не путать с лайком. */
    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError.asStateFlow()

    /** Текущий шаг импорта по ссылке. */
    private val _importState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val importState: StateFlow<PlaylistImportState> = _importState.asStateFlow()

    /** Причина ошибки импорта: текст подбирает UI из строковых ресурсов. */
    private val _importError = MutableStateFlow<ImportError?>(null)
    val importError: StateFlow<ImportError?> = _importError.asStateFlow()

    /** Имя успешно сохранённого плейлиста — для Snackbar об успехе. */
    private val _importSuccess = MutableStateFlow<String?>(null)
    val importSuccess: StateFlow<String?> = _importSuccess.asStateFlow()

    private var importJob: Job? = null

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

    fun consumePlaylistError() {
        _playlistError.value = null
    }

    /** Удаление скачанного трека: файл и запись в Room убирает репозиторий. */
    fun removeDownload(trackId: String) {
        viewModelScope.launch {
            runCatching { downloadRepository.remove(trackId) }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(TAG, "Не удалось удалить скачанный трек", error)
                    _downloadError.value = DELETE_FAILED_MESSAGE
                }
        }
    }

    /**
     * Удаление плейлиста: его треки уходят каскадом внешнего ключа.
     * Сбой БД не крашит экран — показываем Snackbar.
     */
    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching { playlistRepository.deletePlaylist(playlistId) }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(TAG, "Не удалось удалить плейлист", error)
                    _playlistError.value = PLAYLIST_DELETE_FAILED_MESSAGE
                }
        }
    }

    /**
     * Загрузка плейлиста по ссылке: разбор и сеть делает сервис, здесь только
     * состояния для UI. Повторный запуск отменяет предыдущую загрузку.
     */
    fun startImport(link: String) {
        importJob?.cancel()
        _importError.value = null
        _importState.value = PlaylistImportState.Loading
        importJob = viewModelScope.launch {
            when (val result = playlistImportService.load(link)) {
                is PlaylistImportResult.Success -> {
                    _importState.value = PlaylistImportState.Preview(result.playlist)
                }
                is PlaylistImportResult.Failure -> {
                    _importState.value = PlaylistImportState.Idle
                    _importError.value = result.error
                }
            }
        }
    }

    /**
     * Сохранение превью: создаём плейлист и добавляем треки по одному.
     * Ошибка БД не крашит экран — показываем Snackbar и возвращаемся в Idle.
     */
    fun confirmImport() {
        val playlist = (_importState.value as? PlaylistImportState.Preview)?.playlist ?: return
        _importState.value = PlaylistImportState.Saving
        viewModelScope.launch {
            runCatching {
                val playlistId = playlistRepository.createPlaylist(playlist.name)
                playlist.tracks.forEach { track ->
                    playlistRepository.addTrack(playlistId, track)
                }
            }.rethrowCancellation()
                .onSuccess {
                    _importState.value = PlaylistImportState.Idle
                    _importSuccess.value = playlist.name
                }
                .onFailure { error ->
                    Log.e(TAG, "Не удалось сохранить импортированный плейлист", error)
                    _importState.value = PlaylistImportState.Idle
                    _importError.value = ImportError.SAVE_FAILED
                }
        }
    }

    /** Отмена на любом шаге: незавершённая загрузка тоже прекращается. */
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importState.value = PlaylistImportState.Idle
    }

    fun consumeImportError() {
        _importError.value = null
    }

    fun consumeImportSuccess() {
        _importSuccess.value = null
    }

    private companion object {
        const val TAG = "LibraryViewModel"
        const val LIKE_FAILED_MESSAGE = "Не удалось обновить лайк: база данных недоступна"
        const val DELETE_FAILED_MESSAGE = "Не удалось удалить трек с устройства"
        const val PLAYLIST_DELETE_FAILED_MESSAGE = "Не удалось удалить плейлист: база данных недоступна"
    }
}
