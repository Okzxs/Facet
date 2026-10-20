package com.my.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.my.music.data.local.dao.ChartCacheDao
import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.dao.PlayHistoryDao
import com.my.music.data.local.dao.TasteDao
import com.my.music.data.local.entity.ChartCacheEntity
import com.my.music.data.local.entity.LikedTrackEntity
import com.my.music.data.local.entity.PlayHistoryEntity
import com.my.music.data.local.entity.ArtistWeightEntity
import com.my.music.data.local.entity.GenreWeightEntity
import com.my.music.data.local.entity.UserPreferenceEntity

/**
 * Локальная база данных Room приложения MyMusic.
 */
@Database(
    entities = [
        LikedTrackEntity::class,
        PlayHistoryEntity::class,
        ChartCacheEntity::class,
        GenreWeightEntity::class,
        ArtistWeightEntity::class,
        UserPreferenceEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTracksDao(): LikedTracksDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun chartCacheDao(): ChartCacheDao
    abstract fun tasteDao(): TasteDao
}
