package com.facet.music.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.import.ImportError
import com.facet.music.core.import.ImportService
import com.facet.music.core.import.ProfileLinkParser
import com.facet.music.core.import.TextPlaylistParser
import com.facet.music.core.import.TextTrackQuery
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlaylistRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import com.facet.music.data.source.external.SoundCloudApiException
import com.facet.music.data.source.external.SoundCloudLikesPrivateException
import com.facet.music.data.source.external.SoundCloudLikesProvider
import com.facet.music.data.source.imports.PlaylistImportResult
import com.facet.music.data.source.imports.PlaylistImportService
import com.facet.music.data.source.imports.ProfileImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Вкладка экрана импорта. */
enum class ImportTab { PROFILE, TEXT }

/** Почему лайки SoundCloud не загрузились — текст выбирает экран. */
enum class LikesError {
    /** У профиля действительно нет лайков. */
    EMPTY,

    /** API ответил пусто, хотя лайки есть: профиль закрыт или скрыл их. */
    PRIVATE,

    /** HTTP-ошибка api-v2 (например, 401/403 из-за client_id). */
    API,

    /** Сеть недоступна или ответ не разобрался. */
    NETWORK,
}

/** Плейлист профиля с отметкой выбора. */
data class ProfilePlaylistItem(
    val id: String,
    val url: String?,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val selected: Boolean = true,
)

data class ImportUiState(
    val tab: ImportTab = ImportTab.PROFILE,
    val link: String = "",
    val text: String = "",
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val playlists: List<ProfilePlaylistItem> = emptyList(),
    val detectedService: ImportService? = null,
    val error: ImportError? = null,
    val message: String? = null,
    val textQueries: List<TextTrackQuery> = emptyList(),
    val textFound: List<Track> = emptyList(),
    val textMissing: Int = 0,
    val importedPlaylists: Int = 0,
    /** Превью лайков SoundCloud до импорта. */
    val likesPreview: List<Track> = emptyList(),
    val likesImported: Int? = null,
    /** Ошибка загрузки лайков и HTTP-код, если виноват API. */
    val likesError: LikesError? = null,
    val likesApiCode: Int? = null,
    /** id трека → реальное время лайка в SoundCloud. */
    val likesTimes: Map<String, Long> = emptyMap(),
) {
    val selectedCount: Int get() = playlists.count { it.selected }
}

/**
 * Импорт профиля (Spotify/SoundCloud) и текстового списка треков.
 * Сеть и сохранение — в репозиториях, здесь только состояния экрана.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val playlistImportService: PlaylistImportService,
    private val playlistRepository: PlaylistRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val youTubeRepository: YouTubeRepository,
    private val likedTracksRepository: LikedTracksRepository,
    private val soundCloudLikesProvider: SoundCloudLikesProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * Загрузка лайков SoundCloud для превью. client_id берётся публичный —
     * тот же, что использует NewPipe; профиль должен быть публичным.
     */
    fun loadSoundCloudLikes() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    likesPreview = emptyList(),
                    likesError = null,
                    likesApiCode = null,
                    likesTimes = emptyMap(),
                )
            }
            val rawLink = _uiState.value.link
            // Если ссылка распознана как профиль — берём канонический URL;
            // иначе провайдер сам добавит https://soundcloud.com/.
            val profileUrl = ProfileLinkParser.parse(rawLink)
                ?.takeIf { it.service == ImportService.SOUNDCLOUD }
                ?.id
                ?: rawLink.trim().trimStart('@')

            soundCloudLikesProvider.fetchLikes(profileUrl)
                .onSuccess { result ->
                    val emptyReason = when {
                        result.tracks.isNotEmpty() -> null
                        result.likesCount == 0 -> LikesError.EMPTY
                        else -> LikesError.PRIVATE
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            likesPreview = result.tracks,
                            likesError = emptyReason,
                            likesApiCode = null,
                            likesTimes = result.likeTimes,
                        )
                    }
                }
                .onFailure { error ->
                    val api = error as? SoundCloudApiException
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            likesError = when {
                                error is SoundCloudLikesPrivateException -> LikesError.PRIVATE
                                api != null -> LikesError.API
                                else -> LikesError.NETWORK
                            },
                            likesApiCode = api?.code,
                        )
                    }
                }
        }
    }

    /** Сохранение показанных лайков в избранное без дублей. */
    fun importSoundCloudLikes() {
        val state = _uiState.value
        val preview = state.likesPreview
        if (preview.isEmpty() || state.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val existing = runCatching {
                likedTracksRepository.getLikedTracks().first()
            }.getOrDefault(emptyList()).mapTo(HashSet()) { it.id }

            // Время лайка из SoundCloud: избранное сортируется по нему, а не
            // по порядку импорта. Существующим трекам обновляем только дату,
            // чтобы не потерять сезон и геометку «Воспоминаний».
            preview.forEach { track ->
                val likedAt = state.likesTimes[track.id]?.takeIf { it > 0 }
                if (track.id in existing) {
                    if (likedAt != null) {
                        runCatching { likedTracksRepository.updateLikeTime(track.id, likedAt) }
                    }
                } else {
                    runCatching {
                        likedTracksRepository.likeTrack(
                            track = track,
                            likedAt = likedAt ?: System.currentTimeMillis(),
                        )
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    // Считаем все обработанные лайки: существующим обновилась
                    // дата, и пользователь видит реальное число, а не «0 новых».
                    likesImported = preview.size,
                    message = null,
                )
            }
        }
    }

    fun setTab(tab: ImportTab) {
        _uiState.update { it.copy(tab = tab, error = null) }
    }

    fun setLink(link: String) {
        _uiState.update {
            it.copy(
                link = link,
                detectedService = ProfileLinkParser.parse(link)?.service,
                error = null,
            )
        }
    }

    fun setText(text: String) {
        _uiState.update { it.copy(text = text, error = null) }
    }

    fun togglePlaylist(id: String) {
        _uiState.update { state ->
            state.copy(
                playlists = state.playlists.map {
                    if (it.id == id) it.copy(selected = !it.selected) else it
                },
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    /** Показали итог импорта лайков — сбрасываем, чтобы он не повторялся. */
    fun consumeLikesImported() {
        _uiState.update { it.copy(likesImported = null) }
    }

    /** Загрузка публичных плейлистов профиля. */
    fun loadProfile() {
        val link = _uiState.value.link.trim()
        if (link.isEmpty()) {
            _uiState.update { it.copy(error = ImportError.EMPTY_LINK) }
            return
        }
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, playlists = emptyList()) }
            when (val result = playlistImportService.loadProfile(link)) {
                is ProfileImportResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlists = result.playlists.map { playlist ->
                            ProfilePlaylistItem(
                                id = playlist.id,
                                url = playlist.url,
                                name = playlist.name,
                                coverUrl = playlist.coverUrl,
                                trackCount = playlist.trackCount,
                            )
                        },
                    )
                }
                is ProfileImportResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    /** Импорт выбранных плейлистов: каждый становится локальным плейлистом. */
    fun importSelected() {
        val state = _uiState.value
        if (state.isImporting) return
        val selected = state.playlists.filter { it.selected }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            var imported = 0
            for (item in selected) {
                val link = item.url ?: continue
                val loaded = playlistImportService.load(link)
                if (loaded !is PlaylistImportResult.Success) continue
                runCatching {
                    val playlistId = playlistRepository.createPlaylist(loaded.playlist.name)
                    loaded.playlist.tracks.forEach { track ->
                        playlistRepository.addTrack(playlistId, track)
                    }
                }.onSuccess { imported++ }
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importedPlaylists = imported,
                    message = if (imported > 0) {
                        "Импортировано плейлистов: $imported"
                    } else {
                        null
                    },
                    error = if (imported == 0) ImportError.NETWORK else null,
                )
            }
        }
    }

    /** Разбор текста и поиск каждого трека в SoundCloud, затем YouTube. */
    fun parseText() {
        val queries = TextPlaylistParser.parse(_uiState.value.text)
        if (queries.isEmpty()) {
            _uiState.update { it.copy(error = ImportError.EMPTY_LINK) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val found = mutableListOf<Track>()
            var missing = 0
            for (query in queries.take(MAX_TEXT_TRACKS)) {
                val track = findTrack(query)
                if (track != null) found += track else missing++
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    textQueries = queries,
                    textFound = found,
                    textMissing = missing,
                )
            }
        }
    }

    /** Сохранение найденных треков в новый локальный плейлист. */
    fun importText() {
        val state = _uiState.value
        if (state.textFound.isEmpty() || state.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching {
                val playlistId = playlistRepository.createPlaylist("Импорт из списка")
                state.textFound.forEach { playlistRepository.addTrack(playlistId, it) }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importedPlaylists = 1,
                        message = "Импортировано треков: ${state.textFound.size}",
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isImporting = false, error = ImportError.SAVE_FAILED) }
            }
        }
    }

    private suspend fun findTrack(query: TextTrackQuery): Track? {
        val search = listOfNotNull(query.artist, query.title).joinToString(" ")
        soundCloudRepository.search(query = search, limit = 3)
            .getOrNull()
            ?.firstOrNull()
            ?.let { return it }
        return youTubeRepository.search(query = search, limit = 1)
            .getOrNull()
            ?.firstOrNull()
    }

    private companion object {
        /** Больше 200 строк за один заход не ищем: это десятки запросов. */
        const val MAX_TEXT_TRACKS = 200
    }
}
