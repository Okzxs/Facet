package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.facet.music.data.local.entity.LikedTrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с избранными треками (таблица liked_tracks).
 */
@Dao
interface LikedTracksDao {

    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    fun getLikedTracks(): Flow<List<LikedTrackEntity>>

    @Query("SELECT * FROM liked_tracks ORDER BY likedAt DESC")
    suspend fun getAll(): List<LikedTrackEntity>

    @Query("SELECT * FROM liked_tracks WHERE season = :season ORDER BY likedAt DESC")
    fun getLikedTracksBySeason(season: String): Flow<List<LikedTrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE id = :id)")
    fun isLiked(id: String): Flow<Boolean>

    @Query("SELECT * FROM liked_tracks WHERE id = :id LIMIT 1")
    suspend fun getLikedTrack(id: String): LikedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedTrack(track: LikedTrackEntity)

    /** Импорт обновляет только дату лайка, не трогая сезон и геометку. */
    @Query("UPDATE liked_tracks SET likedAt = :likedAt WHERE id = :id")
    suspend fun updateLikedAt(id: String, likedAt: Long)

    @Query("DELETE FROM liked_tracks WHERE id = :id")
    suspend fun deleteLikedTrack(id: String)

    /**
     * Атомарно переключает лайк: чтение и запись выполняются
     * в одной транзакции, поэтому двойной тап не оставит БД в неверном состоянии.
     */
    @Transaction
    suspend fun toggleLikedTrack(track: LikedTrackEntity): Boolean {
        return if (getLikedTrack(track.id) != null) {
            deleteLikedTrack(track.id)
            false
        } else {
            insertLikedTrack(track)
            true
        }
    }
}
