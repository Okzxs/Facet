package com.facet.music.data.local

import android.content.Context
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.player.PlaybackAudioConfig
import com.facet.music.core.security.SecureStore
import com.facet.music.core.text.CensorshipSettings
import com.facet.music.core.service.ServiceType
import com.facet.music.core.wave.WaveMood
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val appContext: Context = context.applicationContext

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val secureStore by lazy { SecureStore(appContext) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()
    private val _settings = MutableStateFlow<AppSettings?>(null)
    override val settings: Flow<AppSettings> = _settings.filterNotNull()

    override val snapshot: AppSettings?
        get() = _settings.value

    init {
        scope.launch {
            _settings.value = load()
            loaded.complete(Unit)
        }
    }

    private fun load(): AppSettings = AppSettings(
        dynamicCoverColors = prefs.getBoolean(KEY_DYNAMIC_COVER_COLORS, false),
        accentColorArgb = prefs.getInt(KEY_ACCENT_COLOR, AppSettings.DEFAULT_ACCENT_ARGB),
        themeMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        customAccentColor = if (prefs.contains(KEY_CUSTOM_ACCENT)) {
            prefs.getInt(KEY_CUSTOM_ACCENT, 0)
        } else {
            null
        },
        recentAccentColors = prefs.getString(KEY_RECENT_ACCENTS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList(),
        accentEnabled = prefs.getBoolean(KEY_ACCENT_ENABLED, false),
        spotifyClientId = prefs.getString(KEY_SPOTIFY_CLIENT_ID, null).orEmpty(),
        spotifyClientSecret = readSpotifyClientSecret(),
        appLanguage = prefs.getString(KEY_APP_LANGUAGE, null)
            ?.takeIf { it in AppSettings.SUPPORTED_LANGUAGES }
            ?: AppSettings.LANGUAGE_SYSTEM,
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
        importCompleted = prefs.getBoolean(KEY_IMPORT_COMPLETED, false),
        spotifyLikesImported = prefs.getBoolean(KEY_SPOTIFY_LIKES_IMPORTED, false),
        downloadQualityKbps = prefs.getInt(KEY_DOWNLOAD_QUALITY, 320)
            .takeIf { it in SettingsRepository.DOWNLOAD_QUALITIES } ?: 320,
        soundcloudClientId = prefs.getString(KEY_SOUNDCLOUD_CLIENT_ID, null).orEmpty(),
        analyticsEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, false),
        resumeAfterFocusLoss = prefs.getBoolean(KEY_RESUME_AFTER_FOCUS_LOSS, true),
        searchSources = loadSearchSources(),
        lyricsTranslationLanguage = prefs.getString(KEY_LYRICS_TRANSLATION_LANGUAGE, null)
            ?.takeIf { it in AppSettings.SUPPORTED_LANGUAGES }
            ?: AppSettings.LANGUAGE_SYSTEM,
        equalizerEnabled = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false),
        equalizerPreset = prefs.getString(KEY_EQUALIZER_PRESET, null)
            ?.takeIf { it in EQUALIZER_PRESETS }
            ?: "FLAT",
        skipSilenceEnabled = prefs.getBoolean(KEY_SKIP_SILENCE, false),
        paletteMode = prefs.getString(KEY_PALETTE_MODE, null)
            ?.takeIf { it in PALETTE_MODES }
            ?: "SIGNATURE",
        serifDisplay = prefs.getBoolean(KEY_SERIF_DISPLAY, false),
        amoledBlack = prefs.getBoolean(KEY_AMOLED_BLACK, false),
        shapeStyle = prefs.getString(KEY_SHAPE_STYLE, null)
            ?.takeIf { it in SHAPE_STYLES }
            ?: "DEFAULT",
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.3f),
        dockStyle = prefs.getString(KEY_DOCK_STYLE, null)
            ?.takeIf { it in AppSettings.DOCK_STYLES }
            ?: AppSettings.DOCK_FLOATING,
        dockLabels = prefs.getString(KEY_DOCK_LABELS, null)
            ?.takeIf { it in AppSettings.DOCK_LABEL_MODES }
            ?: AppSettings.LABELS_ALWAYS,
        tabOrder = prefs.getString(KEY_TAB_ORDER, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it in AppSettings.DEFAULT_TAB_ORDER }
            ?.distinct()
            ?.ifEmpty { AppSettings.DEFAULT_TAB_ORDER }
            ?: AppSettings.DEFAULT_TAB_ORDER,
        hiddenTabs = prefs.getString(KEY_HIDDEN_TABS, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it in AppSettings.DEFAULT_TAB_ORDER }
            ?.toSet()
            ?: emptySet(),
        homeSectionOrder = prefs.getString(KEY_HOME_SECTION_ORDER, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }
            ?.distinct()
            ?.ifEmpty { AppSettings.DEFAULT_HOME_SECTION_ORDER }
            ?: AppSettings.DEFAULT_HOME_SECTION_ORDER,
        hiddenHomeSections = prefs.getString(KEY_HIDDEN_HOME_SECTIONS, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }
            ?.toSet()
            ?: emptySet(),
        homeHeroStyle = prefs.getString(KEY_HOME_HERO_STYLE, null)
            ?.takeIf { it in AppSettings.HERO_STYLES }
            ?: AppSettings.HERO_EDITORIAL,
        homeShowWavePromo = prefs.getBoolean(KEY_HOME_WAVE_PROMO, true),
        playerLayout = prefs.getString(KEY_PLAYER_LAYOUT, null)
            ?.takeIf { it in AppSettings.PLAYER_LAYOUTS }
            ?: AppSettings.LAYOUT_ARTWORK,
        coverShape = prefs.getString(KEY_COVER_SHAPE, null)
            ?.takeIf { it in AppSettings.COVER_SHAPES }
            ?: AppSettings.COVER_AUTO,
        coverSize = prefs.getString(KEY_COVER_SIZE, null)
            ?.takeIf { it in AppSettings.COVER_SIZES }
            ?: AppSettings.COVER_LARGE,
        playerBackdrop = prefs.getString(KEY_PLAYER_BACKDROP, null)
            ?.takeIf { it in AppSettings.PLAYER_BACKDROPS }
            ?: AppSettings.BACKDROP_COVER,
        dismissSensitivity = prefs.getString(KEY_DISMISS_SENSITIVITY, null)
            ?.takeIf { it in AppSettings.DISMISS_SENSITIVITIES }
            ?: AppSettings.SENSITIVITY_MEDIUM,
        progressStyle = prefs.getString(KEY_PROGRESS_STYLE, null)
            ?.takeIf { it in AppSettings.PROGRESS_STYLES }
            ?: AppSettings.PROGRESS_WAVE,
        miniPlayerClose = prefs.getBoolean(KEY_MINI_CLOSE, true),
        miniPlayerProgress = prefs.getBoolean(KEY_MINI_PROGRESS, true),
        lyricScale = prefs.getFloat(KEY_LYRIC_SCALE, 1f).coerceIn(0.85f, 1.25f),
        lyricBold = prefs.getBoolean(KEY_LYRIC_BOLD, true),
        lyricBlur = prefs.getBoolean(KEY_LYRIC_BLUR, true),
        equalizerCustomBands = prefs.getString(KEY_EQ_CUSTOM_BANDS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.size == AppSettings.DEFAULT_EQ_BANDS.size }
            ?.map { it.coerceIn(AppSettings.EQ_MIN_DB, AppSettings.EQ_MAX_DB) }
            ?: AppSettings.DEFAULT_EQ_BANDS,
        libraryPlaylistLayout = prefs.getString(KEY_LIBRARY_PLAYLIST_LAYOUT, null)
            ?.takeIf { it in AppSettings.LIBRARY_LAYOUTS }
            ?: AppSettings.LIBRARY_GRID,
        preferUncensored = prefs.getBoolean(KEY_PREFER_UNCENSORED, true),
        fadeMs = PlaybackAudioConfig.sanitizeFade(prefs.getInt(KEY_FADE_MS, 0)),
        audioBufferProfile = prefs.getString(KEY_AUDIO_BUFFER_PROFILE, null)
            ?.takeIf { it in AppSettings.AUDIO_BUFFER_PROFILES }
            ?: "MEDIUM",
    ).also { CensorshipSettings.preferUncensored = it.preferUncensored }

    private fun loadSearchSources(): Set<ServiceType> {
        val stored = prefs.getString(KEY_SEARCH_SOURCES, null)
        if (stored != null) {
            val parsed = stored.split(',')
                .mapNotNull { name -> ServiceType.entries.firstOrNull { it.name == name.trim() } }
                .toSet()
            return parsed.ifEmpty { ServiceType.entries.toSet() }
        }
        val legacyService = prefs.getString(KEY_LEGACY_SEARCH_SERVICE, null)
            ?.let { name -> ServiceType.entries.firstOrNull { it.name == name } }
        val legacyAll = prefs.getBoolean(KEY_LEGACY_ALL_SOURCES, true)
        return if (legacyService != null && !legacyAll) setOf(legacyService) else ServiceType.entries.toSet()
    }

    private suspend fun awaitLoaded() = loaded.await()

    override suspend fun setDynamicCoverColors(enabled: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COVER_COLORS, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(dynamicCoverColors = enabled) }
    }

    override suspend fun setAccentColorArgb(argb: Int) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_ACCENT_COLOR, argb).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(accentColorArgb = argb) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(themeMode = mode) }
    }

    override suspend fun setCustomAccentColor(argb: Int?) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            if (argb == null) {
                prefs.edit().remove(KEY_CUSTOM_ACCENT).apply()
            } else {
                prefs.edit().putInt(KEY_CUSTOM_ACCENT, argb).apply()
            }
        }
        _settings.update { (it ?: AppSettings()).copy(customAccentColor = argb) }
    }

    override suspend fun setAccentEnabled(enabled: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_ACCENT_ENABLED, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(accentEnabled = enabled) }
    }

    override suspend fun addRecentAccentColor(argb: Int) {
        awaitLoaded()
        val updated = _settings.updateAndGet { current ->
            val base = current ?: AppSettings()
            base.copy(
                recentAccentColors = (listOf(argb) + base.recentAccentColors)
                    .distinct()
                    .take(AppSettings.MAX_RECENT_ACCENTS),
            )
        } ?: return
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_RECENT_ACCENTS, updated.recentAccentColors.joinToString(","))
                .apply()
        }
    }

    override suspend fun setSpotifyCredentials(clientId: String, secret: String) {
        awaitLoaded()
        val trimmedId = clientId.trim()
        val trimmedSecret = secret.trim()
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_SPOTIFY_CLIENT_ID, trimmedId).apply()
            if (trimmedSecret.isEmpty()) {
                secureStore.remove(KEY_SPOTIFY_CLIENT_SECRET)
                prefs.edit().remove(KEY_SPOTIFY_CLIENT_SECRET).apply()
            } else if (secureStore.putString(KEY_SPOTIFY_CLIENT_SECRET, trimmedSecret)) {
                prefs.edit().remove(KEY_SPOTIFY_CLIENT_SECRET).apply()
            }
        }
        _settings.update {
            (it ?: AppSettings()).copy(
                spotifyClientId = trimmedId,
                spotifyClientSecret = trimmedSecret,
            )
        }
    }

    private fun readSpotifyClientSecret(): String {
        if (prefs.contains(KEY_SPOTIFY_CLIENT_SECRET)) {
            secureStore.migrateLegacy(prefs, listOf(KEY_SPOTIFY_CLIENT_SECRET))
        }
        if (!secureStore.contains(KEY_SPOTIFY_CLIENT_SECRET)) return ""
        return secureStore.getString(KEY_SPOTIFY_CLIENT_SECRET).orEmpty()
    }

    override suspend fun setAppLanguage(language: String) {
        awaitLoaded()
        val normalized = language.takeIf { it in AppSettings.SUPPORTED_LANGUAGES }
            ?: AppSettings.LANGUAGE_SYSTEM
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_APP_LANGUAGE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(appLanguage = normalized) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(onboardingCompleted = completed) }
    }

    override suspend fun setImportCompleted(completed: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_IMPORT_COMPLETED, completed).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(importCompleted = completed) }
    }

    override suspend fun setSpotifyLikesImported(imported: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_SPOTIFY_LIKES_IMPORTED, imported).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(spotifyLikesImported = imported) }
    }

    override suspend fun setSoundCloudClientId(clientId: String) {
        awaitLoaded()
        val trimmed = clientId.trim()
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_SOUNDCLOUD_CLIENT_ID, trimmed).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(soundcloudClientId = trimmed) }
    }

    override suspend fun setAnalyticsEnabled(enabled: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(analyticsEnabled = enabled) }
    }

    override suspend fun setResumeAfterFocusLoss(enabled: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_RESUME_AFTER_FOCUS_LOSS, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(resumeAfterFocusLoss = enabled) }
    }

    override suspend fun setDownloadQuality(kbps: Int) {
        awaitLoaded()
        val normalized = kbps.takeIf { it in SettingsRepository.DOWNLOAD_QUALITIES } ?: 320
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_DOWNLOAD_QUALITY, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(downloadQualityKbps = normalized) }
    }

    override suspend fun setWaveMood(mood: WaveMood) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_WAVE_MOOD, mood.name).apply()
        }
    }

    override suspend fun setLyricsTranslationLanguage(language: String) {
        val normalized = if (language in AppSettings.SUPPORTED_LANGUAGES) {
            language
        } else {
            AppSettings.LANGUAGE_SYSTEM
        }
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_LYRICS_TRANSLATION_LANGUAGE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(lyricsTranslationLanguage = normalized) }
    }

    override suspend fun setEqualizerEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(equalizerEnabled = enabled) }
    }

    override suspend fun setEqualizerPreset(preset: String) {
        val normalized = preset.takeIf { it in EQUALIZER_PRESETS } ?: "FLAT"
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_EQUALIZER_PRESET, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(equalizerPreset = normalized) }
    }

    override suspend fun setPaletteMode(mode: String) {
        val normalized = mode.takeIf { it in PALETTE_MODES } ?: "SIGNATURE"
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PALETTE_MODE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(paletteMode = normalized) }
    }

    override suspend fun setSerifDisplay(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_SERIF_DISPLAY, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(serifDisplay = enabled) }
    }

    override suspend fun setAmoledBlack(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_AMOLED_BLACK, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(amoledBlack = enabled) }
    }

    override suspend fun setShapeStyle(style: String) {
        val normalized = style.takeIf { it in SHAPE_STYLES } ?: "DEFAULT"
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_SHAPE_STYLE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(shapeStyle = normalized) }
    }

    override suspend fun setTextScale(scale: Float) {
        val normalized = scale.coerceIn(0.85f, 1.3f)
        withContext(Dispatchers.IO) {
            prefs.edit().putFloat(KEY_TEXT_SCALE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(textScale = normalized) }
    }

    override suspend fun setDockStyle(style: String) {
        val normalized = style.takeIf { it in AppSettings.DOCK_STYLES }
            ?: AppSettings.DOCK_FLOATING
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_DOCK_STYLE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(dockStyle = normalized) }
    }

    override suspend fun setDockLabels(mode: String) {
        val normalized = mode.takeIf { it in AppSettings.DOCK_LABEL_MODES }
            ?: AppSettings.LABELS_ALWAYS
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_DOCK_LABELS, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(dockLabels = normalized) }
    }

    override suspend fun setTabOrder(order: List<String>) {
        val normalized = order.filter { it in AppSettings.DEFAULT_TAB_ORDER }.distinct()
            .ifEmpty { AppSettings.DEFAULT_TAB_ORDER }
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_TAB_ORDER, normalized.joinToString(",")).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(tabOrder = normalized) }
    }

    override suspend fun setHiddenTabs(routes: Set<String>) {
        val normalized = routes.filter { it in AppSettings.DEFAULT_TAB_ORDER }.toSet()
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_HIDDEN_TABS, normalized.joinToString(",")).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(hiddenTabs = normalized) }
    }

    override suspend fun setHomeSectionOrder(order: List<String>) {
        val normalized = order.filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }
            .distinct()
            .ifEmpty { AppSettings.DEFAULT_HOME_SECTION_ORDER }
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_HOME_SECTION_ORDER, normalized.joinToString(","))
                .apply()
        }
        _settings.update { (it ?: AppSettings()).copy(homeSectionOrder = normalized) }
    }

    override suspend fun setHiddenHomeSections(sections: Set<String>) {
        val normalized = sections.filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }.toSet()
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_HIDDEN_HOME_SECTIONS, normalized.joinToString(","))
                .apply()
        }
        _settings.update { (it ?: AppSettings()).copy(hiddenHomeSections = normalized) }
    }

    override suspend fun setHomeHeroStyle(style: String) {
        val normalized = style.takeIf { it in AppSettings.HERO_STYLES }
            ?: AppSettings.HERO_EDITORIAL
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_HOME_HERO_STYLE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(homeHeroStyle = normalized) }
    }

    override suspend fun setHomeShowWavePromo(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_HOME_WAVE_PROMO, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(homeShowWavePromo = enabled) }
    }

    override suspend fun setPlayerLayout(layout: String) {
        val normalized = layout.takeIf { it in AppSettings.PLAYER_LAYOUTS }
            ?: AppSettings.LAYOUT_ARTWORK
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PLAYER_LAYOUT, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(playerLayout = normalized) }
    }

    override suspend fun setCoverShape(shape: String) {
        val normalized = shape.takeIf { it in AppSettings.COVER_SHAPES }
            ?: AppSettings.COVER_AUTO
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_COVER_SHAPE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(coverShape = normalized) }
    }

    override suspend fun setCoverSize(size: String) {
        val normalized = size.takeIf { it in AppSettings.COVER_SIZES }
            ?: AppSettings.COVER_LARGE
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_COVER_SIZE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(coverSize = normalized) }
    }

    override suspend fun setPlayerBackdrop(backdrop: String) {
        val normalized = backdrop.takeIf { it in AppSettings.PLAYER_BACKDROPS }
            ?: AppSettings.BACKDROP_COVER
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PLAYER_BACKDROP, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(playerBackdrop = normalized) }
    }

    override suspend fun setDismissSensitivity(sensitivity: String) {
        val normalized = sensitivity.takeIf { it in AppSettings.DISMISS_SENSITIVITIES }
            ?: AppSettings.SENSITIVITY_MEDIUM
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_DISMISS_SENSITIVITY, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(dismissSensitivity = normalized) }
    }

    override suspend fun setProgressStyle(style: String) {
        val normalized = style.takeIf { it in AppSettings.PROGRESS_STYLES }
            ?: AppSettings.PROGRESS_WAVE
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_PROGRESS_STYLE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(progressStyle = normalized) }
    }

    override suspend fun setMiniPlayerClose(visible: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_MINI_CLOSE, visible).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(miniPlayerClose = visible) }
    }

    override suspend fun setMiniPlayerProgress(visible: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_MINI_PROGRESS, visible).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(miniPlayerProgress = visible) }
    }

    override suspend fun setLyricScale(scale: Float) {
        val normalized = scale.coerceIn(0.85f, 1.25f)
        withContext(Dispatchers.IO) {
            prefs.edit().putFloat(KEY_LYRIC_SCALE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(lyricScale = normalized) }
    }

    override suspend fun setLyricBold(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_LYRIC_BOLD, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(lyricBold = enabled) }
    }

    override suspend fun setLyricBlur(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_LYRIC_BLUR, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(lyricBlur = enabled) }
    }

    override suspend fun setEqualizerCustomBands(bands: List<Float>) {
        val normalized = if (bands.size == AppSettings.DEFAULT_EQ_BANDS.size) {
            bands.map { it.coerceIn(AppSettings.EQ_MIN_DB, AppSettings.EQ_MAX_DB) }
        } else {
            AppSettings.DEFAULT_EQ_BANDS
        }
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_EQ_CUSTOM_BANDS, normalized.joinToString(","))
                .apply()
        }
        _settings.update { (it ?: AppSettings()).copy(equalizerCustomBands = normalized) }
    }

    override suspend fun setLibraryPlaylistLayout(layout: String) {
        val normalized = layout.takeIf { it in AppSettings.LIBRARY_LAYOUTS }
            ?: AppSettings.LIBRARY_GRID
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_LIBRARY_PLAYLIST_LAYOUT, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(libraryPlaylistLayout = normalized) }
    }

    override suspend fun setPreferUncensored(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_PREFER_UNCENSORED, enabled).apply()
        }
        CensorshipSettings.preferUncensored = enabled
        _settings.update { (it ?: AppSettings()).copy(preferUncensored = enabled) }
    }

    override suspend fun setFadeMs(ms: Int) {
        val normalized = PlaybackAudioConfig.sanitizeFade(ms)
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_FADE_MS, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(fadeMs = normalized) }
    }

    override suspend fun setAudioBufferProfile(profile: String) {
        val normalized = profile.takeIf { it in AppSettings.AUDIO_BUFFER_PROFILES } ?: "MEDIUM"
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_AUDIO_BUFFER_PROFILE, normalized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(audioBufferProfile = normalized) }
    }

    override suspend fun setSkipSilenceEnabled(enabled: Boolean) {        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(skipSilenceEnabled = enabled) }
    }

    override suspend fun setSearchSources(sources: Set<ServiceType>) {
        awaitLoaded()
        val normalized = sources.ifEmpty { ServiceType.entries.toSet() }
        val serialized = ServiceType.entries
            .filter { it in normalized }
            .joinToString(",") { it.name }
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_SEARCH_SOURCES, serialized).apply()
        }
        _settings.update { (it ?: AppSettings()).copy(searchSources = normalized) }
    }

    private companion object {
        const val PREFS_NAME = AppLocale.PREFS_NAME
        const val KEY_DYNAMIC_COVER_COLORS = "dynamic_cover_colors"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CUSTOM_ACCENT = "custom_accent_color"
        const val KEY_RECENT_ACCENTS = "recent_accent_colors"
        const val KEY_ACCENT_ENABLED = "accent_enabled"
        const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
        const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_IMPORT_COMPLETED = "import_completed"
        const val KEY_SPOTIFY_LIKES_IMPORTED = "spotify_likes_imported"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_SOUNDCLOUD_CLIENT_ID = "soundcloud_client_id"
        const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        const val KEY_RESUME_AFTER_FOCUS_LOSS = "resume_after_focus_loss"
        const val KEY_SEARCH_SOURCES = "search_sources"
        const val KEY_WAVE_MOOD = "wave_mood"

        const val KEY_LYRICS_TRANSLATION_LANGUAGE = "lyrics_translation_language"

        const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"

        const val KEY_EQUALIZER_PRESET = "equalizer_preset"

        const val KEY_SKIP_SILENCE = "skip_silence"

        const val KEY_PALETTE_MODE = "palette_mode"

        const val KEY_SERIF_DISPLAY = "serif_display"

        val PALETTE_MODES = setOf("DYNAMIC", "SIGNATURE", "NIGHT_JAZZ", "PAPER", "MONO")

        const val KEY_AMOLED_BLACK = "amoled_black"

        const val KEY_SHAPE_STYLE = "shape_style"

        val SHAPE_STYLES = setOf("SOFT", "DEFAULT", "ROUND")

        const val KEY_TEXT_SCALE = "text_scale"

        const val KEY_DOCK_STYLE = "dock_style"

        const val KEY_DOCK_LABELS = "dock_labels"

        const val KEY_TAB_ORDER = "tab_order"

        const val KEY_HIDDEN_TABS = "hidden_tabs"

        const val KEY_HOME_SECTION_ORDER = "home_section_order"

        const val KEY_HIDDEN_HOME_SECTIONS = "hidden_home_sections"

        const val KEY_HOME_HERO_STYLE = "home_hero_style"

        const val KEY_HOME_WAVE_PROMO = "home_wave_promo"

        const val KEY_PLAYER_LAYOUT = "player_layout"

        const val KEY_COVER_SHAPE = "cover_shape"

        const val KEY_COVER_SIZE = "cover_size"

        const val KEY_PLAYER_BACKDROP = "player_backdrop"

        const val KEY_DISMISS_SENSITIVITY = "dismiss_sensitivity"

        const val KEY_PROGRESS_STYLE = "progress_style"

        const val KEY_MINI_CLOSE = "mini_player_close"

        const val KEY_MINI_PROGRESS = "mini_player_progress"

        const val KEY_LYRIC_SCALE = "lyric_scale"

        const val KEY_LYRIC_BOLD = "lyric_bold"

        const val KEY_LYRIC_BLUR = "lyric_blur"

        const val KEY_EQ_CUSTOM_BANDS = "eq_custom_bands"

        const val KEY_LIBRARY_PLAYLIST_LAYOUT = "library_playlist_layout"

        const val KEY_PREFER_UNCENSORED = "prefer_uncensored"

        const val KEY_FADE_MS = "fade_ms"

        const val KEY_AUDIO_BUFFER_PROFILE = "audio_buffer_profile"

        val EQUALIZER_PRESETS = setOf("FLAT", "BASS", "VOCAL", "ROCK", "ELECTRONIC", "CUSTOM")
        const val KEY_LEGACY_SEARCH_SERVICE = "search_service"
        const val KEY_LEGACY_ALL_SOURCES = "search_all_sources"
    }
}
