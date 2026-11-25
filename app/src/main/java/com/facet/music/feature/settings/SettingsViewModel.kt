package com.facet.music.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facet.music.core.player.PlaybackCache
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.core.ui.theme.AppearanceProfile
import com.facet.music.core.ui.theme.AppearanceProfileStore
import com.facet.music.core.ui.theme.applyProfile
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
    private val profileStore: AppearanceProfileStore,
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

    private val _savedStyles = MutableStateFlow(profileStore.names())
    val savedStyles: StateFlow<List<String>> = _savedStyles.asStateFlow()

    private val _studioFeedback = MutableStateFlow<StudioFeedback?>(null)
    val studioFeedback: StateFlow<StudioFeedback?> = _studioFeedback.asStateFlow()

    init {
        refreshCacheStats()
    }

    fun exportProfileJson(): String = AppearanceProfile.from(settings.value).toJson()

    fun importProfileJson(json: String) {
        val profile = AppearanceProfile.parse(json)
        if (profile == null) {
            _studioFeedback.value = StudioFeedback.PROFILE_INVALID
            return
        }
        viewModelScope.launch {
            settingsRepository.applyProfile(profile)
            _studioFeedback.value = StudioFeedback.PROFILE_IMPORTED
        }
    }

    fun resetAppearance() {
        viewModelScope.launch {
            settingsRepository.applyProfile(AppearanceProfile())
            _studioFeedback.value = StudioFeedback.PROFILE_RESET
        }
    }

    fun saveStyle(name: String) {
        val saved = profileStore.save(name, AppearanceProfile.from(settings.value))
        _studioFeedback.value = if (saved) {
            _savedStyles.value = profileStore.names()
            StudioFeedback.STYLE_SAVED
        } else {
            StudioFeedback.INVALID_NAME
        }
    }

    fun applyStyle(name: String) {
        val profile = profileStore.profile(name)
        if (profile == null) {
            _studioFeedback.value = StudioFeedback.PROFILE_INVALID
            return
        }
        viewModelScope.launch {
            settingsRepository.applyProfile(profile)
            _studioFeedback.value = StudioFeedback.STYLE_APPLIED
        }
    }

    fun deleteStyle(name: String) {
        profileStore.delete(name)
        _savedStyles.value = profileStore.names()
        _studioFeedback.value = StudioFeedback.STYLE_DELETED
    }

    fun exportStyleJson(name: String): String? = profileStore.profileJson(name)

    fun consumeStudioFeedback() {
        _studioFeedback.value = null
    }

    fun applyPreset(
        palette: String,
        serif: Boolean,
        shape: String,
        amoled: Boolean,
        themeMode: ThemeMode?,
    ) {
        viewModelScope.launch {
            settingsRepository.setPaletteMode(palette)
            settingsRepository.setSerifDisplay(serif)
            settingsRepository.setShapeStyle(shape)
            settingsRepository.setAmoledBlack(amoled)
            if (themeMode != null) settingsRepository.setThemeMode(themeMode)
            settingsRepository.setAccentEnabled(false)
        }
    }

    fun setDockStyle(style: String) {        viewModelScope.launch { settingsRepository.setDockStyle(style) }
    }

    fun setDockLabels(mode: String) {
        viewModelScope.launch { settingsRepository.setDockLabels(mode) }
    }

    fun moveTab(route: String, offset: Int) {
        val order = settings.value.tabOrder.toMutableList()
        val index = order.indexOf(route)
        if (index < 0) return
        val target = (index + offset).coerceIn(0, order.lastIndex)
        if (target == index) return
        order.removeAt(index)
        order.add(target, route)
        viewModelScope.launch { settingsRepository.setTabOrder(order) }
    }

    fun setTabVisible(route: String, visible: Boolean) {
        val hidden = settings.value.hiddenTabs.toMutableSet()
        if (visible) hidden.remove(route) else hidden.add(route)
        if (hidden.size >= AppSettings.DEFAULT_TAB_ORDER.size) {
            _studioFeedback.value = StudioFeedback.LAST_TAB_REQUIRED
            return
        }
        viewModelScope.launch { settingsRepository.setHiddenTabs(hidden) }
    }

    fun moveHomeSection(section: String, offset: Int) {
        val order = settings.value.homeSectionOrder.toMutableList()
        val index = order.indexOf(section)
        if (index < 0) return
        val target = (index + offset).coerceIn(0, order.lastIndex)
        if (target == index) return
        order.removeAt(index)
        order.add(target, section)
        viewModelScope.launch { settingsRepository.setHomeSectionOrder(order) }
    }

    fun setHomeSectionVisible(section: String, visible: Boolean) {
        val hidden = settings.value.hiddenHomeSections.toMutableSet()
        if (visible) hidden.remove(section) else hidden.add(section)
        viewModelScope.launch { settingsRepository.setHiddenHomeSections(hidden) }
    }

    fun setHomeHeroStyle(style: String) {
        viewModelScope.launch { settingsRepository.setHomeHeroStyle(style) }
    }

    fun setHomeShowWavePromo(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHomeShowWavePromo(enabled) }
    }

    fun setPlayerLayout(layout: String) {
        viewModelScope.launch { settingsRepository.setPlayerLayout(layout) }
    }

    fun setCoverShape(shape: String) {
        viewModelScope.launch { settingsRepository.setCoverShape(shape) }
    }

    fun setCoverSize(size: String) {
        viewModelScope.launch { settingsRepository.setCoverSize(size) }
    }

    fun setPlayerBackdrop(backdrop: String) {
        viewModelScope.launch { settingsRepository.setPlayerBackdrop(backdrop) }
    }

    fun setDismissSensitivity(sensitivity: String) {
        viewModelScope.launch { settingsRepository.setDismissSensitivity(sensitivity) }
    }

    fun setProgressStyle(style: String) {
        viewModelScope.launch { settingsRepository.setProgressStyle(style) }
    }

    fun setMiniPlayerClose(visible: Boolean) {
        viewModelScope.launch { settingsRepository.setMiniPlayerClose(visible) }
    }

    fun setMiniPlayerProgress(visible: Boolean) {
        viewModelScope.launch { settingsRepository.setMiniPlayerProgress(visible) }
    }

    fun setLyricScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setLyricScale(scale) }
    }

    fun setLyricBold(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLyricBold(enabled) }
    }

    fun setLyricBlur(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLyricBlur(enabled) }
    }

    fun setLibraryPlaylistLayout(layout: String) {
        viewModelScope.launch { settingsRepository.setLibraryPlaylistLayout(layout) }
    }

    fun setEqualizerCustomBands(bands: List<Float>) {
        viewModelScope.launch {
            settingsRepository.setEqualizerCustomBands(bands)
            settingsRepository.setEqualizerPreset("CUSTOM")
        }
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

    fun setPaletteMode(mode: String) {
        viewModelScope.launch { settingsRepository.setPaletteMode(mode) }
    }

    fun setSerifDisplay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSerifDisplay(enabled) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAmoledBlack(enabled) }
    }

    fun setShapeStyle(style: String) {
        viewModelScope.launch { settingsRepository.setShapeStyle(style) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTextScale(scale) }
    }

    fun setLyricsTranslationLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setLyricsTranslationLanguage(language)
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

enum class StudioFeedback {
    PROFILE_EXPORTED,
    PROFILE_IMPORTED,
    PROFILE_INVALID,
    PROFILE_RESET,
    STYLE_SAVED,
    STYLE_APPLIED,
    STYLE_DELETED,
    INVALID_NAME,
    LAST_TAB_REQUIRED,
}
