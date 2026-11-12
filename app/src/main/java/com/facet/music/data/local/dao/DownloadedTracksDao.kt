package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTracksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadedTrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :id)")
    fun isDownloaded(id: String): Flow<Boolean>

    @Query("SELECT id FROM downloaded_tracks")
    fun getDownloadedIds(): Flow<List<String>>

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadedTrackEntity>>
}
