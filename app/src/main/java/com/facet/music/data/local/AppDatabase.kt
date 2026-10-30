package com.facet.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.facet.music.data.local.dao.ChartCacheDao
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.local.dao.DownloadedTracksDao
import com.facet.music.data.local.dao.LikedTracksDao
import com.facet.music.data.local.dao.PlayHistoryDao
import com.facet.music.data.local.dao.PlaylistDao
import com.facet.music.data.local.dao.QueueDao
import com.facet.music.data.local.dao.RecentlyShownDao
import com.facet.music.data.local.dao.SearchCacheDao
import com.facet.music.data.local.dao.TasteDao
import com.facet.music.data.local.entity.ChartCacheEntity
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.local.entity.DownloadedTrackEntity
import com.facet.music.data.local.entity.LikedTrackEntity
import com.facet.music.data.local.entity.PlayHistoryEntity
import com.facet.music.data.local.entity.ArtistWeightEntity
import com.facet.music.data.local.entity.GenreWeightEntity
import com.facet.music.data.local.entity.PlaylistEntity
import com.facet.music.data.local.entity.PlaylistTrackEntity
import com.facet.music.data.local.entity.QueueEntity
import com.facet.music.data.local.entity.RecentlyShownEntity
import com.facet.music.data.local.entity.SearchCacheEntity
import com.facet.music.data.local.entity.UserPreferenceEntity

/**
 * Локальная база данных Room приложения Facet.
 */
@Database(
    entities = [
        LikedTrackEntity::class,
        PlayHistoryEntity::class,
        ChartCacheEntity::class,
        GenreWeightEntity::class,
        ArtistWeightEntity::class,
        UserPreferenceEntity::class,
        DownloadedTrackEntity::class,
        RecentlyShownEntity::class,
        DislikedTrackEntity::class,
        SearchCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        QueueEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedTracksDao(): LikedTracksDao
    abstract fun dislikedTrackDao(): DislikedTrackDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun chartCacheDao(): ChartCacheDao
    abstract fun tasteDao(): TasteDao
    abstract fun downloadedTracksDao(): DownloadedTracksDao
    abstract fun recentlyShownDao(): RecentlyShownDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
}
