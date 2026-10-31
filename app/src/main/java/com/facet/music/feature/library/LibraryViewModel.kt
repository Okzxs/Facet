package com.facet.music.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.download.DownloadManager
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.download.DownloadState
import com.facet.music.core.import.ImportError
import com.facet.music.core.import.ImportedPlaylist
import com.facet.music.core.player.PlayerController
import com.facet.music.core.spotify.SpotifyPlaylistSummary
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.model.DownloadedTrack
import com.facet.music.data.model.Playlist
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.DownloadRepository
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlaylistRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SpotifyAuthRepository
import com.facet.music.data.source.external.SpotifyProvider
import com.facet.music.data.source.imports.PlaylistImportResult
import com.facet.music.data.source.imports.PlaylistImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val spotifyAuthRepository: SpotifyAuthRepository,
    private val spotifyProvider: SpotifyProvider,
    private val settingsRepository: SettingsRepository,
    playerController: PlayerController,
) : ViewModel() {

    /** Флаги импорта: скрывают FAB и блок Spotify после первого импорта. */
    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings(),
        )

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

    /** Плейлисты Spotify вошедшего пользователя — строка импорта в медиатеке. */
    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylistSummary>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylistSummary>> = _spotifyPlaylists.asStateFlow()

    private val _spotifyLoading = MutableStateFlow(false)
    val spotifyLoading: StateFlow<Boolean> = _spotifyLoading.asStateFlow()

    /** Не удалось загрузить плейлисты Spotify — показываем Snackbar. */
    private val _spotifyError = MutableStateFlow(false)
    val spotifyError: StateFlow<Boolean> = _spotifyError.asStateFlow()

    /** Вошёл ли пользователь в Spotify: без входа секция скрыта. */
    val spotifyConnected: StateFlow<Boolean> = spotifyAuthRepository.authState
        .map { it.connected }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    init {
        // Как только вход завершён, подтягиваем плейлисты пользователя.
        viewModelScope.launch {
            spotifyAuthRepository.authState.collect { state ->
                if (state.connected) {
                    loadSpotifyPlaylists()
                } else {
                    _spotifyPlaylists.value = emptyList()
                }
            }
        }
    }

    /** Ручное обновление: пользователь мог добавить плейлист в Spotify. */
    fun refreshSpotifyPlaylists() {
        if (spotifyAuthRepository.authState.value.connected) loadSpotifyPlaylists()
    }

    private fun loadSpotifyPlaylists() {
        viewModelScope.launch {
            _spotifyLoading.value = true
            spotifyAuthRepository.fetchMyPlaylists()
                .onSuccess { playlists -> _spotifyPlaylists.value = playlists }
                .onFailure { _spotifyError.value = true }
            _spotifyLoading.value = false
        }
    }

    fun consumeSpotifyError() {
        _spotifyError.value = false
    }

    // ─── Импорт любимых треков Spotify ───────────────────────────────────

    /** Сколько новых лайков добавлено последним импортом; null — результата нет. */
    private val _spotifyLikesImported = MutableStateFlow<Int?>(null)
    val spotifyLikesImported: StateFlow<Int?> = _spotifyLikesImported.asStateFlow()

    private val _spotifyLikesLoading = MutableStateFlow(false)
    val spotifyLikesLoading: StateFlow<Boolean> = _spotifyLikesLoading.asStateFlow()

    private val _spotifyLikesError = MutableStateFlow(false)
    val spotifyLikesError: StateFlow<Boolean> = _spotifyLikesError.asStateFlow()

    /**
     * Импорт любимых треков Spotify: тянем `/v1/me/tracks` постранично и
     * докладываем в нашу БД только те, которых ещё нет в избранном.
     * Без входа ничего не делаем — UI показывает подсказку и гасит кнопку.
     */
    fun importSpotifyLikes() {
        if (_spotifyLikesLoading.value) return
        if (!spotifyAuthRepository.authState.value.connected) {
            _spotifyLikesError.value = true
            return
        }
        viewModelScope.launch {
            _spotifyLikesLoading.value = true
            val existing = runCatching {
                likedTracksRepository.getLikedTracks().first()
            }.getOrDefault(emptyList()).mapTo(HashSet()) { it.id }

            spotifyProvider.fetchLikedTracks()
                .onSuccess { tracks ->
                    val fresh = tracks.filter { it.id !in existing }
                    fresh.forEach { track ->
                        runCatching { likedTracksRepository.likeTrack(track) }
                    }
                    _spotifyLikesImported.value = fresh.size
                    // Успешный импорт прячет чипс и подсказку «Требуется вход».
                    runCatching { settingsRepository.setSpotifyLikesImported(true) }
                    runCatching { settingsRepository.setImportCompleted(true) }
                }
                .onFailure { error ->
                    Log.e(TAG, "Не удалось импортировать любимые Spotify", error)
                    _spotifyLikesError.value = true
                }
            _spotifyLikesLoading.value = false
        }
    }

    fun consumeSpotifyLikesResult() {
        _spotifyLikesImported.value = null
    }

    fun consumeSpotifyLikesError() {
        _spotifyLikesError.value = false
    }

    /**
     * Импорт плейлиста Spotify по id: переиспользуем обычный разбор ссылки,
     * чтобы превью и сохранение шли тем же путём, что и ручная вставка.
     */
    fun importSpotifyPlaylist(id: String) {
        startImport("https://open.spotify.com/playlist/$id")
    }

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
     * Создание пустого плейлиста из медиатеки. Имя обрезается репозиторием;
     * ошибка БД не крашит экран — показываем Snackbar через общий поток.
     */
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { playlistRepository.createPlaylist(name) }
                .rethrowCancellation()
                .onFailure { error ->
                    Log.e(TAG, "Не удалось создать плейлист", error)
                    _playlistError.value = PLAYLIST_CREATE_FAILED_MESSAGE
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
                    runCatching { settingsRepository.setImportCompleted(true) }
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
        const val PLAYLIST_CREATE_FAILED_MESSAGE = "Не удалось создать плейлист: база данных недоступна"
    }
}
