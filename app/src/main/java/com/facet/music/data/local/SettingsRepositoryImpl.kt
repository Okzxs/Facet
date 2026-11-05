package com.facet.music.data.local

import android.content.Context
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.security.SecureStore
import com.facet.music.core.service.ServiceType
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

/**
 * Реализация настроек на SharedPreferences.
 *
 * Чтение вынесено в IO: первый доступ к XML-файлу SharedPreferences на
 * холодном старте заметно блокировал главный поток. До завершения загрузки
 * [settings] не эмитит значения — UI показывает дефолты из initial value,
 * а подписчики получают реальные настройки сразу после фонового чтения.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val appContext: Context = context.applicationContext

    // Открываем лениво: сам getSharedPreferences тоже читает диск.
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Client secret шифруется Tink; Keystore инициализируется только при
    // наличии секрета, поэтому обычные настройки от него не зависят.
    private val secureStore by lazy { SecureStore(appContext) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()
    private val _settings = MutableStateFlow<AppSettings?>(null)
    override val settings: Flow<AppSettings> = _settings.filterNotNull()

    init {
        scope.launch {
            _settings.value = load()
            loaded.complete(Unit)
        }
    }

    private fun load(): AppSettings = AppSettings(
        // По умолчанию — фиолетовая тема, динамика обложки выключена
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
        // По умолчанию акцент берётся с обоев (Dynamic Color).
        accentEnabled = prefs.getBoolean(KEY_ACCENT_ENABLED, false),
        // Ключи Spotify: пустая строка — интеграция не настроена.
        spotifyClientId = prefs.getString(KEY_SPOTIFY_CLIENT_ID, null).orEmpty(),
        spotifyClientSecret = readSpotifyClientSecret(),
        // Язык интерфейса: «как в системе», пока пользователь не выбрал другой.
        appLanguage = prefs.getString(KEY_APP_LANGUAGE, null)
            ?.takeIf { it in AppSettings.SUPPORTED_LANGUAGES }
            ?: AppSettings.LANGUAGE_SYSTEM,
        // Онбординг показываем один раз: флаг пишется кнопкой «Начать».
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
        importCompleted = prefs.getBoolean(KEY_IMPORT_COMPLETED, false),
        spotifyLikesImported = prefs.getBoolean(KEY_SPOTIFY_LIKES_IMPORTED, false),
        // Качество скачивания: неизвестное значение считаем 320.
        downloadQualityKbps = prefs.getInt(KEY_DOWNLOAD_QUALITY, 320)
            .takeIf { it in SettingsRepository.DOWNLOAD_QUALITIES } ?: 320,
        soundcloudClientId = prefs.getString(KEY_SOUNDCLOUD_CLIENT_ID, null).orEmpty(),
        // Статистика — только по явному согласию.
        analyticsEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, false),
        resumeAfterFocusLoss = prefs.getBoolean(KEY_RESUME_AFTER_FOCUS_LOSS, true),
        searchSources = loadSearchSources(),
    )

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

    /**
     * Дожидаемся фонового чтения: до него запись в [_settings] стёрла бы
     * значения, прочитанные с диска, и настройка «откатилась» бы к дефолтам.
     */
    private suspend fun awaitLoaded() = loaded.await()

    override suspend fun setDynamicCoverColors(enabled: Boolean) {
        awaitLoaded()
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COVER_COLORS, enabled).apply()
        }
        // update атомарен: параллельная смена другой настройки не потеряется.
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
        // Свежий цвет в начало, дубликаты убираем, хвост обрезаем до лимита.
        // updateAndGet атомарен: два быстрых тапа не затрут друг друга,
        // а список для записи на диск берётся ровно тот, что ушёл в состояние.
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
                // Открытое значение прошлой версии больше не нужно.
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

    /**
     * Читает client secret из шифрованного хранилища, предварительно перенеся
     * открытое значение прошлых версий. Без секрета Keystore не трогается.
     */
    private fun readSpotifyClientSecret(): String {
        if (prefs.contains(KEY_SPOTIFY_CLIENT_SECRET)) {
            secureStore.migrateLegacy(prefs, listOf(KEY_SPOTIFY_CLIENT_SECRET))
        }
        if (!secureStore.contains(KEY_SPOTIFY_CLIENT_SECRET)) return ""
        return secureStore.getString(KEY_SPOTIFY_CLIENT_SECRET).orEmpty()
    }

    override suspend fun setAppLanguage(language: String) {
        awaitLoaded()
        // Неизвестное значение трактуем как «как в системе», чтобы Activity
        // не осталась без языка после битой записи.
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
        // Имя файла совпадает с AppLocale: язык читает attachBaseContext напрямую.
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
        const val KEY_LEGACY_SEARCH_SERVICE = "search_service"
        const val KEY_LEGACY_ALL_SOURCES = "search_all_sources"
    }
}
