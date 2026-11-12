package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.facet.music.data.local.entity.ChartCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChartCacheDao {

    @Query("SELECT * FROM chart_cache WHERE chartType = :chartType ORDER BY position ASC")
    fun getTracksByChart(chartType: String): Flow<List<ChartCacheEntity>>

    @Query("SELECT * FROM chart_cache WHERE chartType = :chartType ORDER BY position ASC")
    suspend fun getTracksByChartSync(chartType: String): List<ChartCacheEntity>

    @Query("SELECT MAX(cachedAt) FROM chart_cache WHERE chartType = :chartType")
    suspend fun getLastCachedTime(chartType: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<ChartCacheEntity>)

    @Query("DELETE FROM chart_cache WHERE chartType = :chartType")
    suspend fun deleteByChart(chartType: String)

    @Transaction
    suspend fun replaceChart(chartType: String, tracks: List<ChartCacheEntity>) {
        deleteByChart(chartType)
        insertAll(tracks)
    }
}
