package com.facet.music.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Показ трека в волне и его дальнейшая судьба. */
@Entity(
    tableName = "wave_impressions",
    indices = [
        Index("trackKey"),
        Index("artistKey"),
        Index("shownAt"),
    ],
)
data class WaveImpressionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackKey: String,
    val trackId: String,
    val artistKey: String,
    val sessionId: String,
    val origin: String,
    val seedKey: String?,
    val shownAt: Long,
    val outcome: String,
    val playedMs: Long = 0L,
)

/** Сессия волны: нужна для памяти семян и чистки старых показов. */
@Entity(tableName = "wave_sessions")
data class WaveSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val createdAt: Long,
    val randomSeed: Long,
    val mode: String,
    val seedArtistKeys: String,
    val seedTrackKeys: String,
    val isRadio: Boolean,
    val radioSeed: String?,
    val cursor: Int,
    @ColumnInfo(defaultValue = "BALANCE")
    val mood: String = "BALANCE",
    val radioArtistKey: String? = null,
    val seedTracksJson: String? = null,
)

/** Ручная или автоматическая блокировка исполнителя. */
@Entity(tableName = "artist_blocklist")
data class ArtistBlocklistEntity(
    @PrimaryKey
    val artistKey: String,
    val createdAt: Long,
    val until: Long?,
    val reason: String,
)
