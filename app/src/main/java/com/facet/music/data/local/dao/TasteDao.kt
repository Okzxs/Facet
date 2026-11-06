package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.facet.music.data.local.entity.ArtistWeightEntity
import com.facet.music.data.local.entity.GenreWeightEntity
import com.facet.music.data.local.entity.UserPreferenceEntity

/**
 * DAO вкусовых весов и пользовательских предпочтений волны.
 */
@Dao
interface TasteDao {

    @Query("SELECT * FROM genre_weights WHERE weight > 0 ORDER BY weight DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopGenres(limit: Int): List<GenreWeightEntity>

    @Query("SELECT * FROM artist_weights WHERE weight > 0 ORDER BY weight DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopArtists(limit: Int): List<ArtistWeightEntity>

    /** Сырые веса для сортировки по затухающему значению в Kotlin. */
    @Query("SELECT * FROM genre_weights ORDER BY weight DESC LIMIT :limit")
    suspend fun getGenresRaw(limit: Int): List<GenreWeightEntity>

    @Query("SELECT * FROM artist_weights ORDER BY weight DESC LIMIT :limit")
    suspend fun getArtistsRaw(limit: Int): List<ArtistWeightEntity>

    @Query("SELECT * FROM genre_weights WHERE genre = :genre LIMIT 1")
    suspend fun getGenreEntity(genre: String): GenreWeightEntity?

    @Query("SELECT * FROM artist_weights WHERE artist = :artist LIMIT 1")
    suspend fun getArtistEntity(artist: String): ArtistWeightEntity?

    @Query("SELECT weight FROM genre_weights WHERE genre = :genre LIMIT 1")
    suspend fun getGenreWeight(genre: String): Double?

    @Query("SELECT weight FROM artist_weights WHERE artist = :artist LIMIT 1")
    suspend fun getArtistWeight(artist: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenreWeight(entity: GenreWeightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistWeight(entity: ArtistWeightEntity)

    @Transaction
    suspend fun addGenreWeight(genre: String, delta: Double, now: Long) {
        val entity = getGenreEntity(genre)
        val current = entity?.let { decayedWeight(it.weight, it.updatedAt, now) } ?: 0.0
        upsertGenreWeight(GenreWeightEntity(genre = genre, weight = current + delta, updatedAt = now))
    }

    @Transaction
    suspend fun addArtistWeight(artist: String, delta: Double, now: Long) {
        val entity = getArtistEntity(artist)
        val current = entity?.let { decayedWeight(it.weight, it.updatedAt, now) } ?: 0.0
        upsertArtistWeight(
            ArtistWeightEntity(artist = artist, weight = current + delta, updatedAt = now),
        )
    }

    @Query("SELECT preferenceValue FROM user_preferences WHERE preferenceKey = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(entity: UserPreferenceEntity)

    @Query("DELETE FROM genre_weights")
    suspend fun clearGenreWeights()

    @Query("DELETE FROM artist_weights")
    suspend fun clearArtistWeights()
}

/** Полураспад вкуса — 30 дней. */
fun decayedWeight(weight: Double, updatedAt: Long, now: Long): Double {
    val ageDays = ((now - updatedAt).coerceAtLeast(0L) / 86_400_000.0)
    return weight * Math.pow(0.5, ageDays / 30.0)
}
