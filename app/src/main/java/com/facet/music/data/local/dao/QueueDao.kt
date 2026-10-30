package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.facet.music.data.local.entity.QueueEntity

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun getAll(): List<QueueEntity>

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueEntity>)

    /** Очередь перезаписывается целиком: старые позиции не должны остаться. */
    @Transaction
    suspend fun replaceAll(items: List<QueueEntity>) {
        clear()
        insertAll(items)
    }
}
