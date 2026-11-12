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
) {
    val isSpotifyConfigured: Boolean
        get() = spotifyClientId.isNotBlank() && spotifyClientSecret.isNotBlank()

    companion object {
        const val DEFAULT_ACCENT_ARGB: Int = 0xFFA63A2B.toInt()

        const val MAX_RECENT_ACCENTS: Int = 5

        const val LANGUAGE_SYSTEM: String = AppLocale.LANGUAGE_SYSTEM

        val SUPPORTED_LANGUAGES = AppLocale.supportedLanguages
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

    companion object {
        val DOWNLOAD_QUALITIES: List<Int> = listOf(128, 256, 320)
    }
}
