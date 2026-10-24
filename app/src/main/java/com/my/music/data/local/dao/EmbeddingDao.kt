package com.my.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.my.music.data.local.entity.AudioEmbeddingEntity

/**
 * Доступ к локальным аудио-эмбеддингам.
 *
 * Один трек — один вектор. Повторный анализ трека заменяет старую запись.
 */
@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: AudioEmbeddingEntity)

    @Query("SELECT * FROM audio_embeddings WHERE trackId = :trackId LIMIT 1")
    suspend fun getById(trackId: String): AudioEmbeddingEntity?

    @Query("SELECT * FROM audio_embeddings WHERE trackId IN (:trackIds)")
    suspend fun getByIds(trackIds: List<String>): List<AudioEmbeddingEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM audio_embeddings WHERE trackId = :trackId)")
    suspend fun exists(trackId: String): Boolean

    @Query("SELECT * FROM audio_embeddings")
    suspend fun getAll(): List<AudioEmbeddingEntity>

    @Query("DELETE FROM audio_embeddings WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM audio_embeddings")
    suspend fun deleteAll()
}
