package com.facet.music.core.ui.theme

import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.data.repository.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppearanceProfile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val paletteMode: String = "SIGNATURE",
    val customAccentColor: Int? = null,
    val accentEnabled: Boolean = false,
    val amoledBlack: Boolean = false,
    val serifDisplay: Boolean = true,
    val textScale: Float = 1f,
    val shapeStyle: String = "DEFAULT",
    val dockStyle: String = AppSettings.DOCK_FLOATING,
    val dockLabels: String = AppSettings.LABELS_ALWAYS,
    val tabOrder: List<String> = AppSettings.DEFAULT_TAB_ORDER,
    val hiddenTabs: Set<String> = emptySet(),
    val homeSectionOrder: List<String> = AppSettings.DEFAULT_HOME_SECTION_ORDER,
    val hiddenHomeSections: Set<String> = emptySet(),
    val homeHeroStyle: String = AppSettings.HERO_EDITORIAL,
    val homeShowWavePromo: Boolean = true,
    val playerLayout: String = AppSettings.LAYOUT_ARTWORK,
    val coverShape: String = AppSettings.COVER_AUTO,
    val coverSize: String = AppSettings.COVER_LARGE,
    val playerBackdrop: String = AppSettings.BACKDROP_COVER,
    val dismissSensitivity: String = AppSettings.SENSITIVITY_MEDIUM,
    val progressStyle: String = AppSettings.PROGRESS_WAVE,
    val miniPlayerClose: Boolean = true,
    val miniPlayerProgress: Boolean = true,
    val lyricScale: Float = 1f,
    val lyricBold: Boolean = true,
    val lyricBlur: Boolean = true,
) {

    fun sanitized(): AppearanceProfile = copy(
        schemaVersion = SCHEMA_VERSION,
        themeMode = themeMode.takeIf { mode -> ThemeMode.entries.any { it.name == mode } }
            ?: ThemeMode.SYSTEM.name,
        paletteMode = paletteMode.takeIf { it in FacetPalette.entries.map(FacetPalette::name) }
            ?: "SIGNATURE",
        textScale = textScale.coerceIn(0.85f, 1.3f),
        shapeStyle = shapeStyle.takeIf { it in FacetShapeStyle.entries.map(FacetShapeStyle::name) }
            ?: "DEFAULT",
        dockStyle = dockStyle.takeIf { it in AppSettings.DOCK_STYLES }
            ?: AppSettings.DOCK_FLOATING,
        dockLabels = dockLabels.takeIf { it in AppSettings.DOCK_LABEL_MODES }
            ?: AppSettings.LABELS_ALWAYS,
        tabOrder = tabOrder.filter { it in AppSettings.DEFAULT_TAB_ORDER }.distinct()
            .ifEmpty { AppSettings.DEFAULT_TAB_ORDER },
        hiddenTabs = hiddenTabs.filter { it in AppSettings.DEFAULT_TAB_ORDER }.toSet(),
        homeSectionOrder = homeSectionOrder
            .filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }
            .distinct()
            .ifEmpty { AppSettings.DEFAULT_HOME_SECTION_ORDER },
        hiddenHomeSections = hiddenHomeSections
            .filter { it in AppSettings.DEFAULT_HOME_SECTION_ORDER }
            .toSet(),
        homeHeroStyle = homeHeroStyle.takeIf { it in AppSettings.HERO_STYLES }
            ?: AppSettings.HERO_EDITORIAL,
        playerLayout = playerLayout.takeIf { it in AppSettings.PLAYER_LAYOUTS }
            ?: AppSettings.LAYOUT_ARTWORK,
        coverShape = coverShape.takeIf { it in AppSettings.COVER_SHAPES }
            ?: AppSettings.COVER_AUTO,
        coverSize = coverSize.takeIf { it in AppSettings.COVER_SIZES }
            ?: AppSettings.COVER_LARGE,
        playerBackdrop = playerBackdrop.takeIf { it in AppSettings.PLAYER_BACKDROPS }
            ?: AppSettings.BACKDROP_COVER,
        dismissSensitivity = dismissSensitivity
            .takeIf { it in AppSettings.DISMISS_SENSITIVITIES }
            ?: AppSettings.SENSITIVITY_MEDIUM,
        progressStyle = progressStyle.takeIf { it in AppSettings.PROGRESS_STYLES }
            ?: AppSettings.PROGRESS_WAVE,
        lyricScale = lyricScale.coerceIn(0.85f, 1.25f),
    )

    fun toJson(): String = profileJson.encodeToString(AppearanceProfile.serializer(), sanitized())

    companion object {
        const val SCHEMA_VERSION: Int = 1

        private val profileJson = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun from(settings: AppSettings): AppearanceProfile = AppearanceProfile(
            themeMode = settings.themeMode.name,
            paletteMode = settings.paletteMode,
            customAccentColor = settings.customAccentColor,
            accentEnabled = settings.accentEnabled,
            amoledBlack = settings.amoledBlack,
            serifDisplay = settings.serifDisplay,
            textScale = settings.textScale,
            shapeStyle = settings.shapeStyle,
            dockStyle = settings.dockStyle,
            dockLabels = settings.dockLabels,
            tabOrder = settings.orderedTabs(),
            hiddenTabs = settings.hiddenTabs,
            homeSectionOrder = settings.orderedHomeSections(),
            hiddenHomeSections = settings.hiddenHomeSections,
            homeHeroStyle = settings.homeHeroStyle,
            homeShowWavePromo = settings.homeShowWavePromo,
            playerLayout = settings.playerLayout,
            coverShape = settings.coverShape,
            coverSize = settings.coverSize,
            playerBackdrop = settings.playerBackdrop,
            dismissSensitivity = settings.dismissSensitivity,
            progressStyle = settings.progressStyle,
            miniPlayerClose = settings.miniPlayerClose,
            miniPlayerProgress = settings.miniPlayerProgress,
            lyricScale = settings.lyricScale,
            lyricBold = settings.lyricBold,
            lyricBlur = settings.lyricBlur,
        ).sanitized()

        fun parse(json: String): AppearanceProfile? = runCatching {
            profileJson.decodeFromString(AppearanceProfile.serializer(), json).sanitized()
        }.getOrNull()
    }
}

suspend fun SettingsRepository.applyProfile(profile: AppearanceProfile) {
    val safe = profile.sanitized()
    setThemeMode(
        ThemeMode.entries.firstOrNull { it.name == safe.themeMode } ?: ThemeMode.SYSTEM,
    )
    setPaletteMode(safe.paletteMode)
    setCustomAccentColor(safe.customAccentColor)
    setAccentEnabled(safe.accentEnabled)
    setAmoledBlack(safe.amoledBlack)
    setSerifDisplay(safe.serifDisplay)
    setTextScale(safe.textScale)
    setShapeStyle(safe.shapeStyle)
    setDockStyle(safe.dockStyle)
    setDockLabels(safe.dockLabels)
    setTabOrder(safe.tabOrder)
    setHiddenTabs(safe.hiddenTabs)
    setHomeSectionOrder(safe.homeSectionOrder)
    setHiddenHomeSections(safe.hiddenHomeSections)
    setHomeHeroStyle(safe.homeHeroStyle)
    setHomeShowWavePromo(safe.homeShowWavePromo)
    setPlayerLayout(safe.playerLayout)
    setCoverShape(safe.coverShape)
    setCoverSize(safe.coverSize)
    setPlayerBackdrop(safe.playerBackdrop)
    setDismissSensitivity(safe.dismissSensitivity)
    setProgressStyle(safe.progressStyle)
    setMiniPlayerClose(safe.miniPlayerClose)
    setMiniPlayerProgress(safe.miniPlayerProgress)
    setLyricScale(safe.lyricScale)
    setLyricBold(safe.lyricBold)
    setLyricBlur(safe.lyricBlur)
}
