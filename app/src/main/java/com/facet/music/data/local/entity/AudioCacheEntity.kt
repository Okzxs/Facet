package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш прослушанного: файл на диске и метаданные для LRU-очистки.
 * [lastAccessAt] обновляется при каждом обращении к файлу, по нему же
 * удаляются самые старые записи, когда кэш превышает лимит.
 */
@Entity(tableName = "audio_cache")
data class AudioCacheEntity(
    @PrimaryKey val trackId: String,
    val filePath: String,
    val sizeBytes: Long,
    val cachedAt: Long,
    val lastAccessAt: Long,
)
