package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.ArtistCacheEntity
import com.facet.music.data.local.entity.FollowedArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistCacheDao {

    @Query("SELECT * FROM `artist_cache` WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): ArtistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArtistCacheEntity)

    @Query("DELETE FROM `artist_cache` WHERE `fetchedAt` < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM `artist_cache` WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM `artist_cache`")
    suspend fun clear()
}

@Dao
interface FollowedArtistDao {

    @Query("SELECT * FROM `followed_artists` ORDER BY `followedAt` DESC")
    fun observeAll(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT `artistKey` FROM `followed_artists`")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT * FROM `followed_artists` ORDER BY `followedAt` DESC")
    suspend fun getAll(): List<FollowedArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FollowedArtistEntity)

    @Query("DELETE FROM `followed_artists` WHERE `artistKey` = :key")
    suspend fun delete(key: String)
}
