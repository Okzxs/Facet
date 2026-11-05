package com.facet.music.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlayerController
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPage
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistProfile
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.ArtistRepository
import com.facet.music.navigation.ARTIST_NAME_ARG
import com.facet.music.navigation.ARTIST_SOURCE_ARG
import com.facet.music.navigation.ARTIST_URL_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние экрана артиста. */
data class ArtistUiState(
    val name: String = "",
    val profile: ArtistProfile? = null,
    val popular: List<Track> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val releases: List<ArtistRelease> = emptyList(),
    val playlists: List<ArtistPlaylist> = emptyList(),
    val related: List<Artist> = emptyList(),
    val releaseTracks: List<Track> = emptyList(),
    val selectedReleaseTitle: String? = null,
    val isReleaseLoading: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isFromCache: Boolean = false,
    val failed: Boolean = false,
) {
    val displayName: String get() = profile?.name ?: name
}

/**
 * Экран артиста на YouTube/SoundCloud.
 *
 * Точный маршрут сразу отдаёт кэш из Room, затем обновляет страницу; старый
 * маршрут `artist/{name}` сначала ищет лучшего артиста по имени. Релизы,
 * плейлисты и похожие исполнители догружаются после основной шапки — экран
 * не ждёт самую медленную секцию.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistRepository: ArtistRepository,
    playerController: PlayerController,
) : ViewModel() {

    private val routeSource: Source? = savedStateHandle
        .get<String>(ARTIST_SOURCE_ARG)
        ?.let { value -> runCatching { Source.valueOf(value) }.getOrNull() }
        ?.takeIf { it == Source.YOUTUBE || it == Source.SOUNDCLOUD }

    private val routeUrl: String? = savedStateHandle
        .get<String>(ARTIST_URL_ARG)
        ?.takeIf { it.isNotBlank() }

    private val routeName: String = savedStateHandle
        .get<String>(ARTIST_NAME_ARG)
        ?.trim()
        .orEmpty()

    private val _uiState = MutableStateFlow(
        ArtistUiState(name = routeName, isLoading = true),
    )
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    val followedKeys: StateFlow<Set<String>> = artistRepository
        .observeFollowed()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

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

    private var source: Source? = routeSource
    private var url: String? = routeUrl
    private var nextToken: String? = null
    private var secondaryJob: Job? = null
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun loadMore() {
        val source = source ?: return
        val url = url ?: return
        val token = nextToken ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = artistRepository.allTracks(source, url, token).getOrNull()
            if (page == null) {
                _uiState.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            nextToken = page.nextToken
            _uiState.update { current ->
                val merged = (current.allTracks + page.tracks).distinctBy { it.id }
                val added = merged.size - current.allTracks.size
                current.copy(
                    allTracks = merged,
                    isLoadingMore = false,
                    hasMore = page.nextToken != null && added > 0,
                )
            }
        }
    }

    fun openRelease(release: ArtistRelease) {
        val source = source ?: return
        _uiState.update {
            it.copy(
                selectedReleaseTitle = release.title,
                releaseTracks = emptyList(),
                isReleaseLoading = true,
            )
        }
        viewModelScope.launch {
            val tracks = artistRepository.releaseTracks(source, release.url)
                .getOrNull()
                .orEmpty()
            _uiState.update {
                it.copy(
                    releaseTracks = tracks,
                    isReleaseLoading = false,
                    selectedReleaseTitle = if (tracks.isEmpty()) null else it.selectedReleaseTitle,
                )
            }
        }
    }

    fun closeRelease() {
        _uiState.update {
            it.copy(
                selectedReleaseTitle = null,
                releaseTracks = emptyList(),
                isReleaseLoading = false,
            )
        }
    }

    fun toggleFollow() {
        val profile = _uiState.value.profile ?: return
        val followed = profile.key in followedKeys.value
        viewModelScope.launch {
            artistRepository.setFollowed(profile, !followed)
        }
    }

    private fun load() {
        loadJob?.cancel()
        secondaryJob?.cancel()
        loadJob = viewModelScope.launch {
            val source = source
            val url = url
            if (source == null || url == null) {
                resolveByName()
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, failed = false) }

            artistRepository.cached(source, url)?.let { cached ->
                applyPage(cached, fromCache = true)
            }
            fetch(source, url)
        }
    }

    private suspend fun resolveByName() {
        if (routeName.isBlank()) {
            _uiState.update { it.copy(isLoading = false, failed = true) }
            return
        }
        val artist = artistRepository.resolveByName(routeName).getOrNull()
        if (artist == null) {
            _uiState.update { it.copy(isLoading = false, failed = true) }
            return
        }
        source = artist.source
        url = artist.url
        _uiState.update { it.copy(name = artist.name, isLoading = true, failed = false) }
        artistRepository.cached(artist.source, artist.url)?.let { cached ->
            applyPage(cached, fromCache = true)
        }
        fetch(artist.source, artist.url)
    }

    private suspend fun fetch(source: Source, url: String) {
        val result = artistRepository.artist(source, url)
        val page = result.getOrNull()
        if (page == null) {
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    failed = current.profile == null,
                )
            }
            return
        }
        applyPage(page, fromCache = false)
        loadSecondary(page.profile)
    }

    private fun applyPage(page: ArtistPage, fromCache: Boolean) {
        source = page.profile.source
        url = page.profile.url
        nextToken = page.nextPage
        _uiState.update { current ->
            current.copy(
                name = page.profile.name,
                profile = page.profile,
                popular = page.popular,
                allTracks = page.allTracks,
                releases = page.releases.ifEmpty { current.releases },
                playlists = page.playlists.ifEmpty { current.playlists },
                related = page.related.ifEmpty { current.related },
                isLoading = false,
                isLoadingMore = false,
                hasMore = page.nextPage != null,
                isFromCache = fromCache,
                failed = false,
            )
        }
    }

    private fun loadSecondary(profile: ArtistProfile) {
        secondaryJob?.cancel()
        secondaryJob = viewModelScope.launch {
            val releases = artistRepository.releases(profile.source, profile.url)
                .getOrNull()
                .orEmpty()
            if (releases.isNotEmpty()) {
                _uiState.update { it.copy(releases = releases) }
                persist()
            }

            val playlists = artistRepository.playlists(profile.source, profile.url)
                .getOrNull()
                .orEmpty()
            if (playlists.isNotEmpty()) {
                _uiState.update { it.copy(playlists = playlists) }
                persist()
            }

            val related = artistRepository.related(
                profile.source,
                profile.url,
                profile.name,
            ).getOrNull().orEmpty()
            if (related.isNotEmpty()) {
                _uiState.update { it.copy(related = related) }
                persist()
            }
        }
    }

    private suspend fun persist() {
        val state = _uiState.value
        val profile = state.profile ?: return
        artistRepository.store(
            ArtistPage(
                profile = profile,
                popular = state.popular,
                releases = state.releases,
                allTracks = state.allTracks,
                playlists = state.playlists,
                related = state.related,
                nextPage = null,
            ),
        )
    }
}
