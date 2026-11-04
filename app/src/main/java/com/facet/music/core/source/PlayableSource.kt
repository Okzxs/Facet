package com.facet.music.core.source

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что умеет источник. Флаги читает UI (бейджи, чипсы) и резолвер стрима,
 * чтобы не зашивать поведение конкретного сервиса в `when` по [Source].
 */
data class SourceCapabilities(
    val search: Boolean = true,
    val fullStream: Boolean = false,
    val requiresAuth: Boolean = false,
    val supportsDownload: Boolean = false,
)

/** Артист из поиска источника — задел для единого экрана артиста. */
data class PlayableArtist(
    val name: String,
    val artworkUrl: String?,
    val source: Source,
    val id: String? = null,
)

/**
 * Готовая ссылка на поток. [expiresAtMs] позволяет не отдавать протухший URL
 * из памяти; null означает, что срок жизни неизвестен.
 */
data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val isHls: Boolean,
    val expiresAtMs: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val source: Source? = null,
)

/**
 * Один источник с онлайн-поиском и собственным потоком.
 *
 * Провайдер не знает ни о плеере, ни о UI: он отдаёт [Track] и умеет
 * по нему получить [ResolvedStream]. Всё, что не умеет сам, добирает
 * [PlaybackResolver] внешним матчем на SoundCloud и YouTube.
 */
interface PlayableSource {

    val source: Source

    val capabilities: SourceCapabilities

    /**
     * Поиск треков. [page] начинается с 1; источники, у которых нет
     * серверной пагинации, добирают выдачу и отрезают нужное окно.
     *
     * @param useCache разрешено ли отдать ответ из кэша поиска.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        page: Int = 1,
        useCache: Boolean = true,
    ): Result<List<Track>>

    /** Поиск артистов; по умолчанию источник его не поддерживает. */
    suspend fun searchArtists(
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<PlayableArtist>> = Result.success(emptyList())

    /** Готов ли источник к работе: есть ключи/токен пользователя. */
    suspend fun isConfigured(): Boolean = true

    /** Ссылка на поток; источник без собственного потока возвращает ошибку. */
    suspend fun resolveStream(track: Track): Result<ResolvedStream> =
        Result.failure(UnsupportedOperationException("${source.name}: поток недоступен"))

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/** Реестр источников: один `@IntoSet`-биндинг на источник. */
@Singleton
class PlayableSourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards PlayableSource>,
) {

    private val bySource: Map<Source, PlayableSource> = sources.associateBy { it.source }

    fun provider(source: Source): PlayableSource? = bySource[source]

    fun all(): List<PlayableSource> = bySource.values.toList()

    fun capabilities(source: Source): SourceCapabilities? = bySource[source]?.capabilities
}
