package com.facet.music.data.repository

import com.facet.music.data.model.Source

interface SearchCacheRepository {

    suspend fun get(
        query: String,
        source: Source,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): String?

    suspend fun put(query: String, source: Source, json: String)

    suspend fun clear()

    companion object {
        const val DEFAULT_TTL_MS: Long = 24 * 60 * 60 * 1000L
    }
}
