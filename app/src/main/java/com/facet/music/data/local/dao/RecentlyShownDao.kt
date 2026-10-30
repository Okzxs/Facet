package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.RecentlyShownEntity

/**
 * DAO для показанных рекомендаций (таблица recently_shown).
 */
@Dao
interface RecentlyShownDao {

    @Query("SELECT trackId FROM recently_shown WHERE shownAt >= :since")
    suspend fun getTrackIdsSince(since: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RecentlyShownEntity>)

    @Query("DELETE FROM recently_shown WHERE shownAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
