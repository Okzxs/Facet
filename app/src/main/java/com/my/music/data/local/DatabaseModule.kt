package com.my.music.data.local

import android.content.Context
import androidx.room.Room
import com.my.music.data.local.dao.LikedTracksDao
import com.my.music.data.local.dao.PlayHistoryDao
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
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideLikedTracksDao(database: AppDatabase): LikedTracksDao {
        return database.likedTracksDao()
    }

    @Provides
    @Singleton
    fun providePlayHistoryDao(database: AppDatabase): PlayHistoryDao {
        return database.playHistoryDao()
    }
}
