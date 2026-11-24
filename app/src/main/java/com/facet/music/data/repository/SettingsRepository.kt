package com.facet.music.data.repository

import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.service.ServiceType
import com.facet.music.core.wave.WaveMood
import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    LIGHT,

    DARK,

    SYSTEM,
}

data class AppSettings(
    val dynamicCoverColors: Boolean = false,
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customAccentColor: Int? = null,
    val recentAccentColors: List<Int> = emptyList(),
    val accentEnabled: Boolean = false,
    val spotifyClientId: String = "",
    val spotifyClientSecret: String = "",
    val appLanguage: String = LANGUAGE_SYSTEM,
    val onboardingCompleted: Boolean = false,
    val importCompleted: Boolean = false,
    val spotifyLikesImported: Boolean = false,
    val downloadQualityKbps: Int = 320,
    val soundcloudClientId: String = "",
    val analyticsEnabled: Boolean = false,
    val resumeAfterFocusLoss: Boolean = true,
    val searchSources: Set<ServiceType> = ServiceType.entries.toSet(),
    val waveMood: WaveMood = WaveMood.BALANCE,
    val lyricsTranslationLanguage: String = LANGUAGE_SYSTEM,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "FLAT",
    val skipSilenceEnabled: Boolean = false,
    val peakLimiterEnabled: Boolean = true,
    val paletteMode: String = "SIGNATURE",
    val serifDisplay: Boolean = true,
    val amoledBlack: Boolean = false,
    val shapeStyle: String = "DEFAULT",
    val textScale: Float = 1f,
    val dockStyle: String = DOCK_FLOATING,
    val dockLabels: String = LABELS_ALWAYS,
    val tabOrder: List<String> = DEFAULT_TAB_ORDER,
    val hiddenTabs: Set<String> = emptySet(),
    val homeSectionOrder: List<String> = DEFAULT_HOME_SECTION_ORDER,
    val hiddenHomeSections: Set<String> = emptySet(),
    val homeHeroStyle: String = HERO_EDITORIAL,
    val homeShowWavePromo: Boolean = true,
    val playerLayout: String = LAYOUT_ARTWORK,
    val coverShape: String = COVER_AUTO,
    val coverSize: String = COVER_LARGE,
    val playerBackdrop: String = BACKDROP_COVER,
    val dismissSensitivity: String = SENSITIVITY_MEDIUM,
    val progressStyle: String = PROGRESS_WAVE,
    val miniPlayerClose: Boolean = true,
    val miniPlayerProgress: Boolean = true,
) {
    val isSpotifyConfigured: Boolean
        get() = spotifyClientId.isNotBlank() && spotifyClientSecret.isNotBlank()

    fun orderedTabs(): List<String> {
        val known = DEFAULT_TAB_ORDER
        val ordered = tabOrder.filter { it in known }.distinct()
        val visible = (ordered + known.filterNot { it in ordered })
            .filterNot { it in hiddenTabs }
        return visible
    }

    fun orderedHomeSections(): List<String> {
        val known = DEFAULT_HOME_SECTION_ORDER
        val ordered = homeSectionOrder.filter { it in known }.distinct()
        return (ordered + known.filterNot { it in ordered })
            .filterNot { it in hiddenHomeSections }
    }

    companion object {
        const val DEFAULT_ACCENT_ARGB: Int = 0xFFA63A2B.toInt()

        const val MAX_RECENT_ACCENTS: Int = 5

        const val LANGUAGE_SYSTEM: String = AppLocale.LANGUAGE_SYSTEM

        val SUPPORTED_LANGUAGES = AppLocale.supportedLanguages

        const val DOCK_FLOATING: String = "FLOATING"
        const val DOCK_ATTACHED: String = "ATTACHED"
        val DOCK_STYLES = setOf(DOCK_FLOATING, DOCK_ATTACHED)

        const val LABELS_ALWAYS: String = "ALWAYS"
        const val LABELS_SELECTED: String = "SELECTED"
        const val LABELS_NEVER: String = "NEVER"
        val DOCK_LABEL_MODES = setOf(LABELS_ALWAYS, LABELS_SELECTED, LABELS_NEVER)

        val DEFAULT_TAB_ORDER: List<String> = listOf("home", "search", "library", "wave")

        const val HERO_EDITORIAL: String = "EDITORIAL"
        const val HERO_MINIMAL: String = "MINIMAL"
        val HERO_STYLES = setOf(HERO_EDITORIAL, HERO_MINIMAL)

        const val SECTION_HERO: String = "hero"
        const val SECTION_WAVE: String = "wave"
        const val SECTION_NEW_RELEASES: String = "new_releases"
        const val SECTION_CHARTS: String = "charts"
        const val SECTION_GENRES: String = "genres"
        const val SECTION_RECOMMENDATIONS: String = "recommendations"
        const val SECTION_RECENT: String = "recent"
        const val SECTION_MEMORIES: String = "memories"

        const val LAYOUT_ARTWORK: String = "ARTWORK"
        const val LAYOUT_SIDE: String = "SIDE"
        const val LAYOUT_IMMERSIVE: String = "IMMERSIVE"
        val PLAYER_LAYOUTS = setOf(LAYOUT_ARTWORK, LAYOUT_SIDE, LAYOUT_IMMERSIVE)

        const val COVER_AUTO: String = "AUTO"
        const val COVER_CIRCLE: String = "CIRCLE"
        const val COVER_SQUARE: String = "SQUARE"
        val COVER_SHAPES = setOf(COVER_AUTO, COVER_CIRCLE, COVER_SQUARE)

        const val COVER_MEDIUM: String = "MEDIUM"
        const val COVER_LARGE: String = "LARGE"
        val COVER_SIZES = setOf(COVER_MEDIUM, COVER_LARGE)

        const val BACKDROP_COVER: String = "COVER"
        const val BACKDROP_SOLID: String = "SOLID"
        val PLAYER_BACKDROPS = setOf(BACKDROP_COVER, BACKDROP_SOLID)

        const val SENSITIVITY_LOW: String = "LOW"
        const val SENSITIVITY_MEDIUM: String = "MEDIUM"
        const val SENSITIVITY_HIGH: String = "HIGH"
        val DISMISS_SENSITIVITIES = setOf(SENSITIVITY_LOW, SENSITIVITY_MEDIUM, SENSITIVITY_HIGH)

        const val PROGRESS_WAVE: String = "WAVE"
        const val PROGRESS_LINE: String = "LINE"
        val PROGRESS_STYLES = setOf(PROGRESS_WAVE, PROGRESS_LINE)

        val DEFAULT_HOME_SECTION_ORDER: List<String> = listOf(
            SECTION_HERO,
            SECTION_WAVE,
            SECTION_NEW_RELEASES,
            SECTION_CHARTS,
            SECTION_GENRES,
            SECTION_RECOMMENDATIONS,
            SECTION_RECENT,
            SECTION_MEMORIES,
        )
    }
}

interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setDynamicCoverColors(enabled: Boolean)

    suspend fun setAccentColorArgb(argb: Int)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setCustomAccentColor(argb: Int?)

    suspend fun setAccentEnabled(enabled: Boolean)

    suspend fun addRecentAccentColor(argb: Int)

    suspend fun setSpotifyCredentials(clientId: String, secret: String)

    suspend fun setAppLanguage(language: String)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setImportCompleted(completed: Boolean)

    suspend fun setSpotifyLikesImported(imported: Boolean)

    suspend fun setDownloadQuality(kbps: Int)

    suspend fun setSoundCloudClientId(clientId: String)

    suspend fun setAnalyticsEnabled(enabled: Boolean)

    suspend fun setResumeAfterFocusLoss(enabled: Boolean)

    suspend fun setSearchSources(sources: Set<ServiceType>)

    suspend fun setWaveMood(mood: WaveMood)

    suspend fun setLyricsTranslationLanguage(language: String)

    suspend fun setEqualizerEnabled(enabled: Boolean)

    suspend fun setEqualizerPreset(preset: String)

    suspend fun setSkipSilenceEnabled(enabled: Boolean)

    suspend fun setPeakLimiterEnabled(enabled: Boolean)

    suspend fun setPaletteMode(mode: String)

    suspend fun setSerifDisplay(enabled: Boolean)

    suspend fun setAmoledBlack(enabled: Boolean)

    suspend fun setShapeStyle(style: String)

    suspend fun setTextScale(scale: Float)

    suspend fun setDockStyle(style: String)

    suspend fun setDockLabels(mode: String)

    suspend fun setTabOrder(order: List<String>)

    suspend fun setHiddenTabs(routes: Set<String>)

    suspend fun setHomeSectionOrder(order: List<String>)

    suspend fun setHiddenHomeSections(sections: Set<String>)

    suspend fun setHomeHeroStyle(style: String)

    suspend fun setHomeShowWavePromo(enabled: Boolean)

    suspend fun setPlayerLayout(layout: String)

    suspend fun setCoverShape(shape: String)

    suspend fun setCoverSize(size: String)

    suspend fun setPlayerBackdrop(backdrop: String)

    suspend fun setDismissSensitivity(sensitivity: String)

    suspend fun setProgressStyle(style: String)

    suspend fun setMiniPlayerClose(visible: Boolean)

    suspend fun setMiniPlayerProgress(visible: Boolean)

    companion object {
        val DOWNLOAD_QUALITIES: List<Int> = listOf(128, 256, 320)
    }
}
