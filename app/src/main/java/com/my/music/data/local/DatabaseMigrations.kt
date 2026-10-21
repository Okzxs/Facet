package com.my.music.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции схемы MyMusic.
 *
 * v1 → v2: появилась таблица кэша чартов.
 * v2 → v3: к лайкам добавлены координаты для «Воспоминаний».
 * v3 → v4: к лайкам добавлен сезон.
 * v4 → v5: веса жанров/артистов и предпочтения волны.
 * v5 → v6: таблица скачанных треков для офлайн-режима.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chart_cache` (
                `chartType` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`chartType`, `id`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `liked_tracks` ADD COLUMN `latitude` REAL")
        database.execSQL("ALTER TABLE `liked_tracks` ADD COLUMN `longitude` REAL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `liked_tracks` ADD COLUMN `season` TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `genre_weights` (
                `genre` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`genre`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `artist_weights` (
                `artist` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`artist`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_preferences` (
                `preferenceKey` TEXT NOT NULL,
                `preferenceValue` TEXT NOT NULL,
                PRIMARY KEY(`preferenceKey`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `localFilePath` TEXT NOT NULL,
                `downloadedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
)
