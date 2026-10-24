package com.my.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Аудио-эмбеддинг трека для таблицы audio_embeddings в Room.
 *
 * Вектор хранится как BLOB (little-endian Float32), чтобы не зависеть
 * от поддержки FloatArray в конкретной версии Room.
 */
@Entity(tableName = "audio_embeddings")
data class AudioEmbeddingEntity(
    @PrimaryKey
    val trackId: String,
    /** L2-нормализованный вектор YAMNet: 1024 значения по 4 байта. */
    val vector: ByteArray,
    val createdAt: Long = System.currentTimeMillis(),
)
