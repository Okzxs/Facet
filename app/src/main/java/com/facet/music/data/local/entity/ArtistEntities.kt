package com.facet.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Сериализованная страница артиста; TTL проверяется по [fetchedAt]. */
@Entity(tableName = "artist_cache")
data class ArtistCacheEntity(
    @PrimaryKey
    val key: String,
    val json: String,
    val fetchedAt: Long,
)

/** Подписка на артиста: ключ — источник и URL канала. */
@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey
    val artistKey: String,
    val source: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val followedAt: Long = System.currentTimeMillis(),
)
