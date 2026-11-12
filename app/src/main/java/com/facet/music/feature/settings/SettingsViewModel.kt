package com.facet.music.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlaybackCache
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.SpotifyAuthRepository
import com.facet.music.data.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val spotifyAuthRepository: SpotifyAuthRepository,
    private val playbackCache: PlaybackCache,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    val spotifyAuth: StateFlow<SpotifyAuthState> = spotifyAuthRepository.authState

    private val _spotifyAuthUrl = MutableStateFlow<String?>(null)
    val spotifyAuthUrl: StateFlow<String?> = _spotifyAuthUrl.asStateFlow()

    private val _spotifyAuthError = MutableStateFlow(false)
    val spotifyAuthError: StateFlow<Boolean> = _spotifyAuthError.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _cacheCount = MutableStateFlow(0)
    val cacheCount: StateFlow<Int> = _cacheCount.asStateFlow()

    init {
        refreshCacheStats()
    }

    private fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheBytes.value = playbackCache.sizeBytes()
            _cacheCount.value = playbackCache.keyCount()
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackCache.clear()
            refreshCacheStats()
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAnalyticsEnabled(enabled)
        }
    }

    fun setResumeAfterFocusLoss(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setResumeAfterFocusLoss(enabled)
        }
    }

    fun setDownloadQuality(kbps: Int) {
        viewModelScope.launch {
            settingsRepository.setDownloadQuality(kbps)
        }
    }

    fun setDynamicCoverColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicCoverColors(enabled)
        }
    }

    fun setAccentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAccentEnabled(enabled)
        }
    }

    fun setAccentColor(color: Color) {
        val argb = color.toArgb()
        viewModelScope.launch {
            settingsRepository.setAccentColorArgb(argb)
            settingsRepository.setCustomAccentColor(argb)
            settingsRepository.addRecentAccentColor(argb)
            settingsRepository.setAccentEnabled(true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }

    fun saveSpotifyCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            settingsRepository.setSpotifyCredentials(clientId, clientSecret)
        }
    }

    fun signInSpotify(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            settingsRepository.setSpotifyCredentials(clientId, clientSecret)
            spotifyAuthRepository.startAuthorization(clientId)
                .onSuccess { url -> _spotifyAuthUrl.value = url }
                .onFailure { _spotifyAuthError.value = true }
        }
    }

    fun signOutSpotify() {
        viewModelScope.launch { spotifyAuthRepository.signOut() }
    }

    fun consumeSpotifyAuthUrl() {
        _spotifyAuthUrl.value = null
    }

    fun consumeSpotifyAuthError() {
        _spotifyAuthError.value = false
    }

    fun setCustomAccentColor(color: Color?) {
        val argb = color?.toArgb()
        viewModelScope.launch {
            settingsRepository.setCustomAccentColor(argb)
            if (argb != null) {
                settingsRepository.addRecentAccentColor(argb)
                settingsRepository.setAccentEnabled(true)
            }
        }
    }
}
