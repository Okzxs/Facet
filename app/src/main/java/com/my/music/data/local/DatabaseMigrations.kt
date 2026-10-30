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
 * v6 → v7: офлайн-кэш Media3: вместо пути к файлу — URL потока и MIME-тип.
 * v7 → v8: память о треках, уже показанных в рекомендациях.
 * v8 → v9: индексы по горячим полям выборок (сезон и дата лайка, время прослушивания, вес жанра).
 * v9 → v10: таблица дизлайков для фильтрации рекомендаций.
 * v10 → v11: кэш сырых JSON-ответов поиска внешних источников.
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

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Пересоздаём таблицу: localFilePath заменяется на mediaUrl + mimeType.
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `downloaded_tracks_new` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `mediaUrl` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `downloadedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        // Старые записи указывали на локальный mp3 — превращаем путь в file:// URL,
        // который ExoPlayer играет напрямую с диска.
        database.execSQL(
            """
            INSERT OR REPLACE INTO `downloaded_tracks_new`
                (`id`, `title`, `artist`, `artworkUrl`, `durationMs`, `mediaUrl`, `mimeType`, `downloadedAt`)
            SELECT
                `id`,
                `title`,
                `artist`,
                `artworkUrl`,
                `durationMs`,
                'file://' || `localFilePath`,
                'audio/mpeg',
                `downloadedAt`
            FROM `downloaded_tracks`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `downloaded_tracks`")
        database.execSQL("ALTER TABLE `downloaded_tracks_new` RENAME TO `downloaded_tracks`")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recently_shown` (
                `trackId` TEXT NOT NULL,
                `shownAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recently_shown_shownAt` " +
                "ON `recently_shown` (`shownAt`)",
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Имена индексов совпадают с теми, что Room генерирует для Index(...)
        // в сущностях, иначе схема после миграции не сойдётся с 9.json.
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_liked_tracks_season` " +
                "ON `liked_tracks` (`season`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_liked_tracks_likedAt` " +
                "ON `liked_tracks` (`likedAt`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` " +
                "ON `play_history` (`playedAt`)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_genre_weights_weight` " +
                "ON `genre_weights` (`weight`)",
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `disliked_tracks` (
                `trackId` TEXT NOT NULL,
                `dislikedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_cache` (
                `query` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `jsonResponse` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`query`)
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
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
)
