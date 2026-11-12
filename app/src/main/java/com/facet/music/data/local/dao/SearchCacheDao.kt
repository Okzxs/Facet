package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.SearchCacheEntity

@Dao
interface SearchCacheDao {

    @Query("SELECT * FROM search_cache WHERE query = :query AND source = :source LIMIT 1")
    suspend fun get(query: String, source: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM search_cache WHERE query = :query AND source = :source")
    suspend fun delete(query: String, source: String)

    @Query("DELETE FROM search_cache")
    suspend fun clearAll()
}
