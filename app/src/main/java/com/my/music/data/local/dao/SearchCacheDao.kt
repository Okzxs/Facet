package com.my.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.my.music.data.local.entity.SearchCacheEntity

/**
 * DAO кэша ответов поиска внешних источников.
 *
 * Ответы хранятся как есть (сырой JSON), поэтому кэш не зависит от
 * структуры DTO конкретного сервиса.
 */
@Dao
interface SearchCacheDao {

    @Query("SELECT * FROM search_cache WHERE query = :query AND source = :source LIMIT 1")
    suspend fun get(query: String, source: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchCacheEntity)

    /** Удаляет записи старше порога (абсолютное время в миллисекундах). */
    @Query("DELETE FROM search_cache WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    /** Точечное удаление просроченной записи из [get]. */
    @Query("DELETE FROM search_cache WHERE query = :query AND source = :source")
    suspend fun delete(query: String, source: String)

    @Query("DELETE FROM search_cache")
    suspend fun clearAll()
}
