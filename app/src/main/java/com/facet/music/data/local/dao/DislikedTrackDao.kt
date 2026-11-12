package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.DislikedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DislikedTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: DislikedTrackEntity)

    @Query("DELETE FROM disliked_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT * FROM disliked_tracks ORDER BY dislikedAt DESC")
    suspend fun getAll(): List<DislikedTrackEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM disliked_tracks WHERE trackId = :trackId)")
    fun isDisliked(trackId: String): Flow<Boolean>
}
