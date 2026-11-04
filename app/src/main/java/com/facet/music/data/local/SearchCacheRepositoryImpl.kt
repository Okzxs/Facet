package com.facet.music.data.local

import com.facet.music.data.local.dao.SearchCacheDao
import com.facet.music.data.local.entity.SearchCacheEntity
import com.facet.music.data.model.Source
import com.facet.music.data.repository.SearchCacheRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация кэша поиска поверх Room.
 *
 * Кэш переживает перезапуск приложения: при старте ничего не сбрасывается,
 * просроченные записи удаляются лениво — при чтении и при каждой записи.
 */
@Singleton
class SearchCacheRepositoryImpl @Inject constructor(
    private val dao: SearchCacheDao,
) : SearchCacheRepository {

    override suspend fun get(query: String, source: Source, ttlMs: Long): String? {
        // Deezer и Apple больше не источники поиска: их старый кэш не отдаём.
        if (source == Source.DEEZER || source == Source.APPLE) return null
        val entity = dao.get(query, source.name) ?: return null
        val ageMs = System.currentTimeMillis() - entity.timestamp
        if (ageMs > ttlMs) {
            // Просроченный ответ не должен занимать место и путать следующие запросы.
            dao.delete(query, source.name)
            return null
        }
        return entity.jsonResponse
    }

    override suspend fun put(query: String, source: Source, json: String) {
        if (source == Source.DEEZER || source == Source.APPLE) return
        val now = System.currentTimeMillis()
        dao.upsert(
            SearchCacheEntity(
                query = query,
                source = source.name,
                jsonResponse = json,
                timestamp = now,
            ),
        )
        // Периодическая чистка: каждая запись в кэш подметает просроченные строки,
        // поэтому отдельный фоновый воркер не нужен.
        dao.deleteOlderThan(now - SearchCacheRepository.DEFAULT_TTL_MS)
    }

    override suspend fun clear() {
        dao.clearAll()
    }
}
