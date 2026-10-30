package com.my.music.data.local

import android.content.Context
import androidx.room.Room
import com.my.music.data.local.dao.DislikedTrackDao
import com.my.music.data.local.dao.DownloadedTracksDao
import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.dao.PlayHistoryDao
import com.my.music.data.local.dao.RecentlyShownDao
import com.my.music.data.local.dao.SearchCacheDao
import com.my.music.data.local.dao.TasteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mymusic.db",
        )
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideLikedTracksDao(database: AppDatabase): LikedTracksDao {
        return database.likedTracksDao()
    }

    @Provides
    @Singleton
    fun provideDislikedTrackDao(database: AppDatabase): DislikedTrackDao {
        return database.dislikedTrackDao()
    }

    @Provides
    @Singleton
    fun providePlayHistoryDao(database: AppDatabase): PlayHistoryDao {
        return database.playHistoryDao()
    }

    @Provides
    @Singleton
    fun provideChartCacheDao(database: AppDatabase): com.my.music.data.local.dao.ChartCacheDao {
        return database.chartCacheDao()
    }

    @Provides
    @Singleton
    fun provideTasteDao(database: AppDatabase): TasteDao {
        return database.tasteDao()
    }

    @Provides
    @Singleton
    fun provideDownloadedTracksDao(database: AppDatabase): DownloadedTracksDao {
        return database.downloadedTracksDao()
    }

    @Provides
    @Singleton
    fun provideRecentlyShownDao(database: AppDatabase): RecentlyShownDao {
        return database.recentlyShownDao()
    }

    @Provides
    @Singleton
    fun provideSearchCacheDao(database: AppDatabase): SearchCacheDao {
        return database.searchCacheDao()
    }
}
