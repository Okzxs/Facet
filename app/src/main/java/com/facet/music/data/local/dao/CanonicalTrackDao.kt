package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.CanonicalTrackEntity
import com.facet.music.data.local.entity.TrackVariantEntity

@Dao
interface CanonicalTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariant(entity: TrackVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariants(entities: List<TrackVariantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCanonical(entity: CanonicalTrackEntity)

    @Query("SELECT * FROM track_variants WHERE canonicalKey = :canonicalKey")
    suspend fun variants(canonicalKey: String): List<TrackVariantEntity>

    @Query("SELECT * FROM track_variants WHERE trackId = :trackId LIMIT 1")
    suspend fun variantByTrackId(trackId: String): TrackVariantEntity?

    @Query("SELECT * FROM canonical_tracks WHERE canonicalKey = :canonicalKey")
    suspend fun canonical(canonicalKey: String): CanonicalTrackEntity?

    @Query("SELECT * FROM canonical_tracks WHERE primaryTrackId = :trackId LIMIT 1")
    suspend fun canonicalByPrimaryTrackId(trackId: String): CanonicalTrackEntity?

    @Query("DELETE FROM track_variants WHERE trackId = :trackId")
    suspend fun deleteVariant(trackId: String)

    @Query("DELETE FROM track_variants WHERE lastCheckedAt < :threshold")
    suspend fun deleteVariantsOlderThan(threshold: Long): Int

    @Query("DELETE FROM canonical_tracks WHERE updatedAt < :threshold")
    suspend fun deleteCanonicalOlderThan(threshold: Long): Int
}
