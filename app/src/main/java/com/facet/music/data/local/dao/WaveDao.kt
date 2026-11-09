package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.ArtistBlocklistEntity
import com.facet.music.data.local.entity.WaveImpressionEntity
import com.facet.music.data.local.entity.WaveSessionEntity

@Dao
interface WaveDao {

    @Insert
    suspend fun insertImpression(entity: WaveImpressionEntity): Long

    @Query("SELECT * FROM wave_impressions WHERE shownAt >= :since ORDER BY shownAt DESC")
    suspend fun impressionsSince(since: Long): List<WaveImpressionEntity>

    @Query("SELECT * FROM wave_impressions WHERE trackKey = :trackKey ORDER BY shownAt DESC LIMIT 1")
    suspend fun latestImpression(trackKey: String): WaveImpressionEntity?

    @Query(
        "UPDATE wave_impressions SET outcome = :outcome, playedMs = :playedMs " +
            "WHERE id = (" +
            "SELECT id FROM wave_impressions WHERE sessionId = :sessionId AND trackKey = :trackKey " +
            "ORDER BY shownAt DESC LIMIT 1)",
    )
    suspend fun updateOutcome(
        sessionId: String,
        trackKey: String,
        outcome: String,
        playedMs: Long,
    ): Int

    @Query(
        "UPDATE wave_impressions SET outcome = :outcome, playedMs = :playedMs " +
            "WHERE id = (" +
            "SELECT id FROM wave_impressions WHERE trackKey = :trackKey " +
            "ORDER BY shownAt DESC LIMIT 1)",
    )
    suspend fun updateLatestOutcome(trackKey: String, outcome: String, playedMs: Long): Int

    @Query(
        "SELECT COUNT(*) FROM wave_impressions " +
            "WHERE artistKey = :artistKey AND outcome = 'SKIPPED' " +
            "AND playedMs < :thresholdMs AND shownAt >= :since",
    )
    suspend fun countFastSkips(artistKey: String, since: Long, thresholdMs: Long): Int

    @Query("DELETE FROM wave_impressions WHERE shownAt < :cutoff")
    suspend fun deleteImpressionsBefore(cutoff: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: WaveSessionEntity)

    @Query("SELECT * FROM wave_sessions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun sessions(limit: Int): List<WaveSessionEntity>

    @Query("SELECT * FROM wave_sessions ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestSession(): WaveSessionEntity?

    @Query("SELECT seedTrackKeys FROM wave_sessions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentSeedTrackKeys(limit: Int): List<String>

    @Query(
        "DELETE FROM wave_sessions WHERE sessionId NOT IN (" +
            "SELECT sessionId FROM wave_sessions ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun trimSessions(keep: Int)

    @Query("UPDATE wave_sessions SET cursor = :cursor WHERE sessionId = :sessionId")
    suspend fun updateCursor(sessionId: String, cursor: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlock(entity: ArtistBlocklistEntity)

    @Query("SELECT * FROM artist_blocklist")
    suspend fun blocklist(): List<ArtistBlocklistEntity>

    @Query("DELETE FROM artist_blocklist WHERE until IS NOT NULL AND until <= :now")
    suspend fun deleteExpiredBlocks(now: Long)

    @Query("DELETE FROM wave_impressions")
    suspend fun clearImpressions()

    @Query("DELETE FROM wave_sessions")
    suspend fun clearSessions()
}
