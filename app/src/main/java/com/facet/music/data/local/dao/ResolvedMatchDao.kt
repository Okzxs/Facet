package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.ResolvedMatchEntity

@Dao
interface ResolvedMatchDao {

    @Query("SELECT * FROM resolved_matches WHERE originalTrackId = :trackId")
    suspend fun get(trackId: String): ResolvedMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolvedMatchEntity)

    @Query("DELETE FROM resolved_matches WHERE originalTrackId = :trackId")
    suspend fun delete(trackId: String)
}
