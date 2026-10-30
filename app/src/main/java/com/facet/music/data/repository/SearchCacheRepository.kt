package com.facet.music.data.repository

import com.facet.music.data.model.Source

/**
 * Кэш сырых JSON-ответов поиска внешних источников.
 *
 * Хранит ответ как есть, поэтому один и тот же разбор (`Json.parseToJsonElement`)
 * подходит и для свежего, и для закэшированного тела.
 */
interface SearchCacheRepository {

    /**
     * JSON из кэша, если запись моложе [ttlMs].
     * Просроченная запись удаляется и не отдаётся вызывающему.
     */
    suspend fun get(
        query: String,
        source: Source,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): String?

    /** Сохраняет успешный ответ и попутно вычищает просроченные записи. */
    suspend fun put(query: String, source: Source, json: String)

    /** Полная очистка кэша. */
    suspend fun clear()

    companion object {
        /** Время жизни записи — сутки: за это время цены/ссылки превью не меняются. */
        const val DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L
    }
}
