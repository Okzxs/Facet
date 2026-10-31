package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.AudioCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioCacheDao {

    @Query("SELECT * FROM audio_cache ORDER BY lastAccessAt DESC")
    fun observeAll(): Flow<List<AudioCacheEntity>>

    @Query("SELECT * FROM audio_cache WHERE trackId = :trackId")
    suspend fun get(trackId: String): AudioCacheEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM audio_cache")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT * FROM audio_cache ORDER BY lastAccessAt ASC LIMIT :count")
    suspend fun oldest(count: Int): List<AudioCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioCacheEntity)

    @Query("UPDATE audio_cache SET lastAccessAt = :accessedAt WHERE trackId = :trackId")
    suspend fun touch(trackId: String, accessedAt: Long)

    @Query("DELETE FROM audio_cache WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM audio_cache")
    suspend fun clear()
}
