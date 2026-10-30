package com.my.music.core.source

import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что умеет источник. Флаги читает UI (бейджи, чипсы) и резолвер стрима,
 * чтобы не зашивать поведение конкретного сервиса в `when` по [Source].
 *
 * @param search поиск по текстовому запросу
 * @param preview отдаёт только короткий отрывок (Deezer/iTunes — 30 секунд)
 * @param fullStream умеет отдать полный трек без внешнего матчинга
 * @param requiresAuth нужен токен/ключи пользователя
 * @param supportsDownload из источника можно собрать цельный файл
 */
data class SourceCapabilities(
    val search: Boolean = true,
    val preview: Boolean = false,
    val fullStream: Boolean = false,
    val requiresAuth: Boolean = false,
    val supportsDownload: Boolean = false,
)

/**
 * Один внешний источник музыки: метаданные + (если умеет) ссылка на поток.
 *
 * Провайдер не знает ни о плеере, ни о UI: он только отдаёт [Track] и умеет
 * по нему получить [AudioStreamResult]. Всё, что не умеет сам, добирает
 * [StreamResolver] внешним матчем на YouTube.
 */
interface MusicSourceProvider {

    /** Доменный источник, который представляет провайдер. */
    val source: Source

    /** Возможности источника — по ним строятся бейджи и проверки доступности. */
    val capabilities: SourceCapabilities

    /**
     * Поиск треков. Ошибка сети/сервиса — [Result.failure], не исключение.
     *
     * @param useCache разрешено ли отдать ответ из кэша поиска. Проверка
     *   доступности сервиса идёт мимо кэша ([useCache] = false), иначе статус
     *   «онлайн» показывался бы из сохранённого ответа без обращения к сети.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        useCache: Boolean = true,
    ): Result<List<Track>>

    /**
     * Готов ли источник к работе: есть ключи/токен пользователя.
     * Проверка статусов спрашивает это до сети: ненастроенный источник
     * помечается недоступным без лишнего запроса.
     */
    suspend fun isConfigured(): Boolean = true

    /**
     * Ссылка на поток, если источник умеет отдать её сам.
     * По умолчанию источник считается «только метаданные».
     */
    suspend fun resolve(track: Track): Result<AudioStreamResult> =
        Result.failure(UnsupportedOperationException("${source.name}: поток недоступен"))

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/**
 * Реестр провайдеров. Позволяет добавлять источник одним `@IntoSet`-биндингом,
 * не трогая поиск, статусы и резолвер.
 */
@Singleton
class MusicSourceRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards MusicSourceProvider>,
) {

    private val bySource: Map<Source, MusicSourceProvider> = providers.associateBy { it.source }

    fun provider(source: Source): MusicSourceProvider? = bySource[source]

    fun all(): List<MusicSourceProvider> = bySource.values.toList()

    fun capabilities(source: Source): SourceCapabilities? = bySource[source]?.capabilities
}
