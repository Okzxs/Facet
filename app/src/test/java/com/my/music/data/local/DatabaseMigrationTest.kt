package com.my.music.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.local.entity.ChartCacheEntity
import com.my.music.data.local.entity.DislikedTrackEntity
import com.my.music.data.local.entity.DownloadedTrackEntity
import com.my.music.data.local.entity.GenreWeightEntity
import com.my.music.data.local.entity.PlaylistEntity
import com.my.music.data.local.entity.PlaylistTrackEntity
import com.my.music.data.local.entity.RecentlyShownEntity
import com.my.music.data.local.entity.SearchCacheEntity
import com.my.music.data.local.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationTest {

    private val databaseName = "migration-test.db"
    private lateinit var context: Context

    /** Helpers открывают легаси-базы; закрываем их все в tearDown. */
    private val openedHelpers = mutableListOf<SupportSQLiteOpenHelper>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        openedHelpers.forEach { it.close() }
        openedHelpers.clear()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migration from version 1 to 12 preserves likes and creates all tables`() = runBlocking {
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val liked = database.likedTracksDao().getLikedTrack("legacy")
            assertEquals("Legacy track", liked?.title)
            assertNull(liked?.latitude)
            assertNull(liked?.longitude)
            assertNull(liked?.season)

            val history = database.playHistoryDao().getPlayHistory().first()
            assertEquals(listOf("legacy"), history.map { it.id })

            database.chartCacheDao().insertAll(
                listOf(
                    ChartCacheEntity(
                        chartType = "new_and_hot",
                        id = "chart-1",
                        position = 1,
                        title = "Chart track",
                        artist = "Artist",
                        artworkUrl = null,
                        durationMs = 60_000L,
                        genre = null,
                        transcodingUrl = "https://soundcloud.com/test/chart-1",
                    ),
                ),
            )
            val chart = database.chartCacheDao().getTracksByChartSync("new_and_hot")
            assertEquals(1, chart.size)
            assertTrue(chart.single().cachedAt > 0)

            assertEquals(12, database.openHelper.writableDatabase.version)

            // Индексы миграции 8 → 9 созданы на живой схеме
            val migrated = database.openHelper.writableDatabase
            assertTrue(hasIndex(migrated, "index_liked_tracks_season"))
            assertTrue(hasIndex(migrated, "index_liked_tracks_likedAt"))
            assertTrue(hasIndex(migrated, "index_play_history_playedAt"))
            assertTrue(hasIndex(migrated, "index_genre_weights_weight"))

            // Таблица офлайн-режима создана миграцией 5 → 6 и обновлена в 6 → 7
            database.downloadedTracksDao().insert(
                DownloadedTrackEntity(
                    id = "downloaded-1",
                    title = "Offline track",
                    artist = "Offline artist",
                    artworkUrl = null,
                    durationMs = 60_000L,
                    mediaUrl = "https://api.soundcloud.com/media/offline.m3u8",
                    mimeType = "application/x-mpegURL",
                    downloadedAt = 5_000L,
                ),
            )
            assertTrue(database.downloadedTracksDao().isDownloaded("downloaded-1").first())
            assertEquals(1, database.downloadedTracksDao().getAll().first().size)

            database.tasteDao().upsertGenreWeight(
                GenreWeightEntity(genre = "rock", weight = 2.5, updatedAt = 2_000L),
            )
            database.tasteDao().upsertPreference(
                UserPreferenceEntity(
                    preferenceKey = "pref_ru_language",
                    preferenceValue = "false",
                ),
            )

            assertEquals(
                2.5,
                database.tasteDao().getGenreWeight("rock") ?: 0.0,
                0.0001,
            )
            assertEquals("false", database.tasteDao().getPreference("pref_ru_language"))
            assertNull(database.tasteDao().getPreference("missing"))

            // Таблица памяти рекомендаций создана миграцией 7 → 8
            database.recentlyShownDao().upsertAll(
                listOf(
                    RecentlyShownEntity(trackId = "shown-1", shownAt = 5_000L),
                ),
            )
            assertEquals(
                listOf("shown-1"),
                database.recentlyShownDao().getTrackIdsSince(1_000L),
            )

            // Таблица дизлайков создана миграцией 9 → 10
            database.dislikedTrackDao().insert(
                DislikedTrackEntity(trackId = "disliked-1", dislikedAt = 7_000L),
            )
            assertEquals(
                listOf("disliked-1"),
                database.dislikedTrackDao().getAll().map { it.trackId },
            )

            // Таблица кэша поиска создана миграцией 10 → 11
            database.searchCacheDao().upsert(
                SearchCacheEntity(
                    query = "cachalot",
                    source = "DEEZER",
                    jsonResponse = """{"data":[]}""",
                    timestamp = 9_000L,
                ),
            )
            assertEquals(
                "DEEZER",
                database.searchCacheDao().get("cachalot", "DEEZER")?.source,
            )

            // Таблицы плейлистов созданы миграцией 11 → 12 и работают вместе с FK.
            val playlistId = database.playlistDao().insertPlaylist(
                PlaylistEntity(name = "Дорога", createdAt = 10_000L),
            )
            database.playlistDao().insertTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = "playlist-track-1",
                    title = "Playlist track",
                    artist = "Artist",
                    artworkUrl = null,
                    durationMs = 60_000L,
                    source = "SOUNDCLOUD",
                    transcodingUrl = "https://soundcloud.com/test/playlist-track-1",
                    addedAt = 11_000L,
                ),
            )
            assertEquals(1, database.playlistDao().count(playlistId))
            assertEquals(
                "playlist-track-1",
                database.playlistDao().observeTracks(playlistId).first().single().trackId,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 6 to 7 converts legacy local file path into media url`() {
        val databaseName = "migration-6-7-test.db"
        context.deleteDatabase(databaseName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
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

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val db = helper.writableDatabase
            db.execSQL(
                """
                INSERT INTO `downloaded_tracks`
                    (`id`, `title`, `artist`, `artworkUrl`, `durationMs`, `localFilePath`, `downloadedAt`)
                VALUES
                    ('legacy-offline', 'Offline track', 'Artist', NULL, 60000,
                     '/data/user/0/com.my.music/files/downloads/offline.mp3', 5000)
                """.trimIndent(),
            )

            MIGRATION_6_7.migrate(db)

            db.query(
                "SELECT `mediaUrl`, `mimeType` FROM `downloaded_tracks` WHERE `id` = 'legacy-offline'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "file:///data/user/0/com.my.music/files/downloads/offline.mp3",
                    cursor.getString(0),
                )
                assertEquals("audio/mpeg", cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    // ─── Изолированные тесты отдельных миграций ───────────────────────────────
    // Схемы v1–v3 не экспортировались в app/schemas, поэтому базовые схемы
    // восстанавливаются DDL-заглушками: для проверки ALTER/CREATE этого достаточно.

    @Test
    fun `migration 2 to 3 adds coordinates columns to likes`() {
        val db = openLegacyDatabase("m-2-3.db", 2) { it.execSQL(LIKED_TRACKS_V1_DDL) }

        MIGRATION_2_3.migrate(db)

        val columns = tableColumns(db, "liked_tracks")
        assertTrue("latitude" in columns)
        assertTrue("longitude" in columns)
    }

    @Test
    fun `migration 3 to 4 adds season column to likes`() {
        val db = openLegacyDatabase("m-3-4.db", 3) {
            it.execSQL(LIKED_TRACKS_V1_DDL)
            it.execSQL("ALTER TABLE `liked_tracks` ADD COLUMN `latitude` REAL")
            it.execSQL("ALTER TABLE `liked_tracks` ADD COLUMN `longitude` REAL")
        }

        MIGRATION_3_4.migrate(db)

        assertTrue("season" in tableColumns(db, "liked_tracks"))
    }

    @Test
    fun `migration 4 to 5 creates taste tables`() {
        val db = openLegacyDatabase("m-4-5.db", 4) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(PLAY_HISTORY_DDL)
        }

        MIGRATION_4_5.migrate(db)

        assertTrue(hasTable(db, "genre_weights"))
        assertTrue(hasTable(db, "artist_weights"))
        assertTrue(hasTable(db, "user_preferences"))
    }

    @Test
    fun `migration 5 to 6 creates downloaded tracks table`() {
        val db = openLegacyDatabase("m-5-6.db", 5) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(PLAY_HISTORY_DDL)
            it.execSQL(GENRE_WEIGHTS_DDL)
        }

        MIGRATION_5_6.migrate(db)

        assertTrue(hasTable(db, "downloaded_tracks"))
        assertTrue("localFilePath" in tableColumns(db, "downloaded_tracks"))
    }

    @Test
    fun `migration 7 to 8 creates recently shown table with index`() {
        val db = openLegacyDatabase("m-7-8.db", 7) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(DOWNLOADED_TRACKS_V7_DDL)
        }

        MIGRATION_7_8.migrate(db)

        assertTrue(hasTable(db, "recently_shown"))
        assertTrue(hasIndex(db, "index_recently_shown_shownAt"))
    }

    @Test
    fun `migration 9 to 10 creates disliked tracks table`() {
        val db = openLegacyDatabase("m-9-10.db", 9) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
        }

        MIGRATION_9_10.migrate(db)

        assertTrue(hasTable(db, "disliked_tracks"))
        assertEquals(
            setOf("trackId", "dislikedAt"),
            tableColumns(db, "disliked_tracks"),
        )
    }

    @Test
    fun `migration 8 to 9 creates indexes for hot fields`() {
        val db = openLegacyDatabase("m-8-9.db", 8) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(PLAY_HISTORY_DDL)
            it.execSQL(GENRE_WEIGHTS_DDL)
        }

        MIGRATION_8_9.migrate(db)

        assertTrue(hasIndex(db, "index_liked_tracks_season"))
        assertTrue(hasIndex(db, "index_liked_tracks_likedAt"))
        assertTrue(hasIndex(db, "index_play_history_playedAt"))
        assertTrue(hasIndex(db, "index_genre_weights_weight"))
    }

    @Test
    fun `migration 10 to 11 creates search cache table`() {
        val db = openLegacyDatabase("m-10-11.db", 10) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
        }

        MIGRATION_10_11.migrate(db)

        assertTrue(hasTable(db, "search_cache"))
        assertEquals(
            setOf("query", "source", "jsonResponse", "timestamp"),
            tableColumns(db, "search_cache"),
        )
        assertEquals(listOf("query"), primaryKeyColumns(db, "search_cache"))
    }

    /**
     * Миграция обязана давать ровно ту схему, что Room экспортировал в 11.json:
     * иначе после обновления приложения база не откроется (Room schema validation).
     */
    @Test
    fun `schema 11 json matches migrated search cache table`() {
        val schemaFile = schemaFile("11.json")
            ?: error("Не найден app/schemas/.../11.json — KSP не экспортировал схему")
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val table = root["database"]?.jsonObject
            ?.get("entities")?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["tableName"]?.jsonPrimitive?.content == "search_cache" }
            ?: error("В 11.json нет таблицы search_cache")

        assertEquals(
            listOf("query", "source", "jsonResponse", "timestamp"),
            table["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("query"),
            table["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray
                .map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `migration 11 to 12 creates playlist tables with cascade`() {
        val db = openLegacyDatabase("m-11-12.db", 11) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
        }

        MIGRATION_11_12.migrate(db)

        assertTrue(hasTable(db, "playlists"))
        assertEquals(setOf("id", "name", "createdAt"), tableColumns(db, "playlists"))
        assertEquals(listOf("id"), primaryKeyColumns(db, "playlists"))

        assertTrue(hasTable(db, "playlist_tracks"))
        assertEquals(
            setOf(
                "playlistId",
                "trackId",
                "title",
                "artist",
                "artworkUrl",
                "durationMs",
                "source",
                "transcodingUrl",
                "addedAt",
            ),
            tableColumns(db, "playlist_tracks"),
        )
        assertEquals(listOf("playlistId", "trackId"), primaryKeyColumns(db, "playlist_tracks"))
        assertTrue(hasIndex(db, "index_playlist_tracks_playlistId"))

        // Внешний ключ с ON DELETE CASCADE: удаление плейлиста чистит треки.
        db.query("PRAGMA foreign_key_list(`playlist_tracks`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("playlists", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO `playlists` (`id`, `name`, `createdAt`) VALUES (7, 'Test', 1000)")
        db.execSQL(
            """
            INSERT INTO `playlist_tracks`
                (`playlistId`, `trackId`, `title`, `artist`, `artworkUrl`, `durationMs`,
                 `source`, `transcodingUrl`, `addedAt`)
            VALUES
                (7, 't1', 'Title', 'Artist', NULL, 60000, 'SOUNDCLOUD', NULL, 2000)
            """.trimIndent(),
        )
        assertEquals(1, countRows(db, "playlist_tracks"))

        db.execSQL("DELETE FROM `playlists` WHERE `id` = 7")
        assertEquals(0, countRows(db, "playlist_tracks"))
    }

    /**
     * Миграция обязана давать ровно ту схему, что Room экспортировал в 12.json:
     * иначе после обновления приложения база не откроется (Room schema validation).
     */
    @Test
    fun `schema 12 json matches migrated playlist tables`() {
        val schemaFile = schemaFile("12.json")
            ?: error("Не найден app/schemas/.../12.json — KSP не экспортировал схему")
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val entities = root["database"]?.jsonObject
            ?.get("entities")?.jsonArray
            ?.map { it.jsonObject }
            ?: error("В 12.json нет списка entities")

        val playlists = entities.firstOrNull {
            it["tableName"]?.jsonPrimitive?.content == "playlists"
        } ?: error("В 12.json нет таблицы playlists")
        assertEquals(
            listOf("id", "name", "createdAt"),
            playlists["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("id"),
            playlists["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertTrue(playlists["primaryKey"]!!.jsonObject["autoGenerate"]!!.jsonPrimitive.boolean)

        val tracks = entities.firstOrNull {
            it["tableName"]?.jsonPrimitive?.content == "playlist_tracks"
        } ?: error("В 12.json нет таблицы playlist_tracks")
        assertEquals(
            listOf(
                "playlistId",
                "trackId",
                "title",
                "artist",
                "artworkUrl",
                "durationMs",
                "source",
                "transcodingUrl",
                "addedAt",
            ),
            tracks["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("playlistId", "trackId"),
            tracks["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray
                .map { it.jsonPrimitive.content },
        )

        val foreignKey = tracks["foreignKeys"]!!.jsonArray.single().jsonObject
        assertEquals("playlists", foreignKey["table"]!!.jsonPrimitive.content)
        assertEquals("CASCADE", foreignKey["onDelete"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("playlistId"),
            foreignKey["columns"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("id"),
            foreignKey["referencedColumns"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val index = tracks["indices"]!!.jsonArray.single().jsonObject
        assertEquals(
            "index_playlist_tracks_playlistId",
            index["name"]!!.jsonPrimitive.content,
        )
        assertEquals(
            listOf("playlistId"),
            index["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    // ─── Хелперы ──────────────────────────────────────────────────────────────

    /** Открывает сырую базу версии [version] со схемой [create] для прогона одной миграции. */
    private fun openLegacyDatabase(
        name: String,
        version: Int,
        create: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteDatabase {
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        openedHelpers += helper
        return helper.writableDatabase
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val names = mutableSetOf<String>()
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            return names
        }
    }

    private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT 1 FROM `sqlite_master` WHERE `type` = 'table' AND `name` = '$table'")
            .use { return it.moveToFirst() }
    }

    private fun hasIndex(db: SupportSQLiteDatabase, index: String): Boolean {
        db.query("SELECT 1 FROM `sqlite_master` WHERE `type` = 'index' AND `name` = '$index'")
            .use { return it.moveToFirst() }
    }

    /** Число строк в таблице — для проверки каскадного удаления. */
    private fun countRows(db: SupportSQLiteDatabase, table: String): Int {
        db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Колонки первичного ключа в порядке составного ключа. */
    private fun primaryKeyColumns(db: SupportSQLiteDatabase, table: String): List<String> {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            val columns = mutableListOf<Pair<Int, String>>()
            while (cursor.moveToNext()) {
                val pk = cursor.getInt(pkIndex)
                if (pk > 0) columns += pk to cursor.getString(nameIndex)
            }
            return columns.sortedBy { it.first }.map { it.second }
        }
    }

    /** Ищет экспортированную схему независимо от рабочей папки теста. */
    private fun schemaFile(name: String): File? {
        val relative = "schemas/com.my.music.data.local.AppDatabase/$name"
        return listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        ).firstOrNull { it.isFile }
    }

    private companion object {
        /** liked_tracks до координат (схемы v1–v2). */
        val LIKED_TRACKS_V1_DDL = """
            CREATE TABLE IF NOT EXISTS `liked_tracks` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `likedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent()

        /** liked_tracks состояния v4: координаты и сезон уже на месте. */
        val LIKED_TRACKS_V4_DDL = """
            CREATE TABLE IF NOT EXISTS `liked_tracks` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `likedAt` INTEGER NOT NULL,
                `latitude` REAL,
                `longitude` REAL,
                `season` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent()

        val PLAY_HISTORY_DDL = """
            CREATE TABLE IF NOT EXISTS `play_history` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `playedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent()

        val GENRE_WEIGHTS_DDL = """
            CREATE TABLE IF NOT EXISTS `genre_weights` (
                `genre` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`genre`)
            )
        """.trimIndent()

        /** downloaded_tracks состояния v7: mediaUrl + mimeType. */
        val DOWNLOADED_TRACKS_V7_DDL = """
            CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
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
        """.trimIndent()
    }

    private fun createVersion1Database() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `liked_tracks` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `likedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `play_history` (
                `id` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `playedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `liked_tracks`
                (`id`, `source`, `title`, `artist`, `artworkUrl`, `durationMs`, `genre`, `transcodingUrl`, `likedAt`)
            VALUES
                ('legacy', 'SOUNDCLOUD', 'Legacy track', 'Legacy artist', NULL, 60000, NULL, 'https://soundcloud.com/test/legacy', 1000)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `play_history`
                (`id`, `source`, `title`, `artist`, `artworkUrl`, `durationMs`, `genre`, `transcodingUrl`, `playedAt`)
            VALUES
                ('legacy', 'SOUNDCLOUD', 'Legacy track', 'Legacy artist', NULL, 60000, NULL, 'https://soundcloud.com/test/legacy', 1000)
            """.trimIndent(),
        )
        database.version = 1
        database.close()
    }
}
