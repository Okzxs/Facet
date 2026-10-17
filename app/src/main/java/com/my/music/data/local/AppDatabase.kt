package com.my.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.dao.PlayHistoryDao
import com.my.music.data.local.entity.LikedTrackEntity
import com.my.music.data.local.entity.PlayHistoryEntity

/**
 * Локальная база данных Room приложения MyMusic.
 */
@Database(
    entities = [
        LikedTrackEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTracksDao(): LikedTracksDao
    abstract fun playHistoryDao(): PlayHistoryDao
}
