package com.my.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.my.music.data.local.entity.DislikedTrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для дизлайков (таблица disliked_tracks).
 */
@Dao
interface DislikedTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: DislikedTrackEntity)

    @Query("DELETE FROM disliked_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT * FROM disliked_tracks ORDER BY dislikedAt DESC")
    suspend fun getAll(): List<DislikedTrackEntity>

    /** Наблюдение за дизлайком конкретного трека — для подсветки кнопки в плеере. */
    @Query("SELECT EXISTS(SELECT 1 FROM disliked_tracks WHERE trackId = :trackId)")
    fun isDisliked(trackId: String): Flow<Boolean>
}
