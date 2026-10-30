package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.facet.music.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с историей воспроизведения (таблица play_history).
 * Хранит последние 50 записей.
 */
@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getPlayHistory(limit: Int = 50): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE id NOT IN (SELECT id FROM play_history ORDER BY playedAt DESC LIMIT :keepCount)")
    suspend fun trimHistory(keepCount: Int = 50)

    @Transaction
    suspend fun recordPlay(item: PlayHistoryEntity, keepCount: Int = 50) {
        insertPlayHistory(item)
        trimHistory(keepCount)
    }

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
