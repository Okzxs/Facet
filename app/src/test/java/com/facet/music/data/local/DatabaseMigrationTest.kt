package com.facet.music.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.facet.music.data.local.entity.ArtistCacheEntity
import com.facet.music.data.local.entity.ChartCacheEntity
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.local.entity.DownloadedTrackEntity
import com.facet.music.data.local.entity.FollowedArtistEntity
import com.facet.music.data.local.entity.GenreWeightEntity
import com.facet.music.data.local.entity.PlaylistEntity
import com.facet.music.data.local.entity.PlaylistTrackEntity
import com.facet.music.data.local.entity.RecentlyShownEntity
import com.facet.music.data.local.entity.SearchCacheEntity
import com.facet.music.data.local.entity.UserPreferenceEntity
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
    fun `migration from version 1 to 19 preserves likes and creates all tables`() = runBlocking {
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

            assertEquals(19, database.openHelper.writableDatabase.version)
            assertTrue(hasTable(database.openHelper.writableDatabase, "resolved_matches"))
            assertTrue(!hasTable(database.openHelper.writableDatabase, "audio_cache"))
            assertTrue(hasTable(database.openHelper.writableDatabase, "artist_cache"))
            assertTrue(hasTable(database.openHelper.writableDatabase, "followed_artists"))
            assertTrue(hasTable(database.openHelper.writableDatabase, "wave_impressions"))
            assertTrue(hasTable(database.openHelper.writableDatabase, "wave_sessions"))
            assertTrue(hasTable(database.openHelper.writableDatabase, "artist_blocklist"))
            assertTrue(
                "seedTracksJson" in tableColumns(database.openHelper.writableDatabase, "wave_sessions"),
            )
            assertTrue("artistUrl" in tableColumns(database.openHelper.writableDatabase, "liked_tracks"))
            assertTrue(
                "playedFraction" in tableColumns(database.openHelper.writableDatabase, "play_history"),
            )

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

            // Кэш артиста и подписки появились в 16 → 17.
            database.artistCacheDao().upsert(
                ArtistCacheEntity(key = "YOUTUBE:test", json = "{}", fetchedAt = 12_000L),
            )
            assertEquals("{}", database.artistCacheDao().get("YOUTUBE:test")?.json)
            database.followedArtistDao().insert(
                FollowedArtistEntity(
                    artistKey = "YOUTUBE:test",
                    source = "YOUTUBE",
                    url = "https://www.youtube.com/channel/test",
                    name = "Test artist",
                    avatarUrl = null,
                    followedAt = 13_000L,
                ),
            )
            assertEquals(listOf("YOUTUBE:test"), database.followedArtistDao().observeKeys().first())
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
                     '/data/user/0/com.facet.music/files/downloads/offline.mp3', 5000)
                """.trimIndent(),
            )

            MIGRATION_6_7.migrate(db)

            db.query(
                "SELECT `mediaUrl`, `mimeType` FROM `downloaded_tracks` WHERE `id` = 'legacy-offline'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "file:///data/user/0/com.facet.music/files/downloads/offline.mp3",
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
    fun `migration 16 to 17 creates artist cache follows and artist url columns`() {
        val db = openLegacyDatabase("m-16-17.db", 16) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(PLAY_HISTORY_DDL)
            it.execSQL(CHART_CACHE_V1_DDL)
            it.execSQL(DOWNLOADED_TRACKS_V7_DDL)
            it.execSQL(PLAYLIST_TRACKS_V12_DDL)
            it.execSQL(QUEUE_V13_DDL)
        }

        MIGRATION_16_17.migrate(db)

        assertTrue(hasTable(db, "artist_cache"))
        assertEquals(
            setOf("key", "json", "fetchedAt"),
            tableColumns(db, "artist_cache"),
        )
        assertTrue(hasTable(db, "followed_artists"))
        assertEquals(
            setOf("artistKey", "source", "url", "name", "avatarUrl", "followedAt"),
            tableColumns(db, "followed_artists"),
        )
        listOf(
            "liked_tracks",
            "play_history",
            "chart_cache",
            "downloaded_tracks",
            "playlist_tracks",
            "queue",
        ).forEach { table ->
            assertTrue("artistUrl отсутствует в $table", "artistUrl" in tableColumns(db, table))
        }
        db.execSQL(
            "INSERT INTO `artist_cache` (`key`, `json`, `fetchedAt`) " +
                "VALUES ('YOUTUBE:test', '{}', 1000)",
        )
        assertEquals(1, countRows(db, "artist_cache"))
        db.execSQL(
            "INSERT INTO `followed_artists` " +
                "(`artistKey`, `source`, `url`, `name`, `avatarUrl`, `followedAt`) " +
                "VALUES ('YOUTUBE:test', 'YOUTUBE', 'https://youtube.com/channel/test', " +
                "'Artist', NULL, 1000)",
        )
        assertEquals(1, countRows(db, "followed_artists"))
    }

    @Test
    fun `migration 18 to 19 adds session mood and stored seeds`() {
        val db = openLegacyDatabase("m-18-19.db", 18) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wave_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `randomSeed` INTEGER NOT NULL,
                    `mode` TEXT NOT NULL,
                    `seedArtistKeys` TEXT NOT NULL,
                    `seedTrackKeys` TEXT NOT NULL,
                    `isRadio` INTEGER NOT NULL,
                    `radioSeed` TEXT,
                    `cursor` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`)
                )
                """.trimIndent(),
            )
        }

        MIGRATION_18_19.migrate(db)

        assertTrue("mood" in tableColumns(db, "wave_sessions"))
        assertTrue("radioArtistKey" in tableColumns(db, "wave_sessions"))
        assertTrue("seedTracksJson" in tableColumns(db, "wave_sessions"))
        db.execSQL(
            "INSERT INTO `wave_sessions` " +
                "(`sessionId`,`createdAt`,`randomSeed`,`mode`,`seedArtistKeys`," +
                "`seedTrackKeys`,`isRadio`,`radioSeed`,`cursor`) " +
                "VALUES ('s',1,1,'NORMAL','[]','[]',0,NULL,0)",
        )
        assertEquals("BALANCE", queryString(db, "SELECT mood FROM wave_sessions WHERE sessionId='s'"))
    }

    @Test
    fun `migration 17 to 18 creates wave tables and played fraction`() {
        val db = openLegacyDatabase("m-17-18.db", 17) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(PLAY_HISTORY_DDL)
        }

        MIGRATION_17_18.migrate(db)

        assertTrue(hasTable(db, "wave_impressions"))
        assertEquals(
            setOf(
                "id",
                "trackKey",
                "trackId",
                "artistKey",
                "sessionId",
                "origin",
                "seedKey",
                "shownAt",
                "outcome",
                "playedMs",
            ),
            tableColumns(db, "wave_impressions"),
        )
        assertTrue(hasIndex(db, "index_wave_impressions_trackKey"))
        assertTrue(hasIndex(db, "index_wave_impressions_artistKey"))
        assertTrue(hasIndex(db, "index_wave_impressions_shownAt"))
        assertTrue(hasTable(db, "wave_sessions"))
        assertEquals(
            setOf(
                "sessionId",
                "createdAt",
                "randomSeed",
                "mode",
                "seedArtistKeys",
                "seedTrackKeys",
                "isRadio",
                "radioSeed",
                "cursor",
            ),
            tableColumns(db, "wave_sessions"),
        )
        assertTrue(hasTable(db, "artist_blocklist"))
        assertEquals(
            setOf("artistKey", "createdAt", "until", "reason"),
            tableColumns(db, "artist_blocklist"),
        )
        assertTrue("playedFraction" in tableColumns(db, "play_history"))

        db.execSQL(
            """
            INSERT INTO `wave_impressions`
                (`trackKey`, `trackId`, `artistKey`, `sessionId`, `origin`, `seedKey`,
                 `shownAt`, `outcome`, `playedMs`)
            VALUES ('artist|song', 'id-1', 'artist', 's-1', 'RELATED', 'seed',
                    1000, 'SHOWN', 0)
            """.trimIndent(),
        )
        assertEquals(1, countRows(db, "wave_impressions"))
        db.execSQL(
            """
            INSERT INTO `wave_sessions`
                (`sessionId`, `createdAt`, `randomSeed`, `mode`, `seedArtistKeys`,
                 `seedTrackKeys`, `isRadio`, `radioSeed`, `cursor`)
            VALUES ('s-1', 1000, 42, 'NORMAL', '[]', '[]', 0, NULL, 1)
            """.trimIndent(),
        )
        assertEquals(1, countRows(db, "wave_sessions"))
        db.execSQL(
            """
            INSERT INTO `artist_blocklist` (`artistKey`, `createdAt`, `until`, `reason`)
            VALUES ('artist', 1000, NULL, 'USER')
            """.trimIndent(),
        )
        assertEquals(1, countRows(db, "artist_blocklist"))
    }

    @Test
    fun `migration 15 to 16 drops audio cache and creates resolved matches`() {
        val db = openLegacyDatabase("m-15-16.db", 15) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(AUDIO_CACHE_V14_DDL)
            it.execSQL(
                "INSERT INTO `audio_cache` (`trackId`, `filePath`, `sizeBytes`, `cachedAt`, `lastAccessAt`) " +
                    "VALUES ('sc:1', '/tmp/1', 100, 1, 1)",
            )
        }

        MIGRATION_15_16.migrate(db)

        assertTrue(!hasTable(db, "audio_cache"))
        assertTrue(hasTable(db, "resolved_matches"))
        assertEquals(
            setOf("originalTrackId", "matchedSource", "matchedUrl", "confidence", "createdAt"),
            tableColumns(db, "resolved_matches"),
        )
        db.execSQL(
            """
            INSERT INTO `resolved_matches`
                (`originalTrackId`, `matchedSource`, `matchedUrl`, `confidence`, `createdAt`)
            VALUES ('spotify:1', 'YOUTUBE', 'https://youtube.com/watch?v=1', 0.82, 1000)
            """.trimIndent(),
        )
        assertEquals(
            1,
            countWhere(db, "resolved_matches", "originalTrackId = 'spotify:1'"),
        )
    }

    @Test
    fun `migration 14 to 15 clears deezer and apple preview streams and caches`() {
        val db = openLegacyDatabase("m-14-15.db", 14) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
            it.execSQL(QUEUE_V13_DDL)
            it.execSQL(SEARCH_CACHE_V10_DDL)
            it.execSQL(AUDIO_CACHE_V14_DDL)
            it.execSQL(
                """
                INSERT INTO `queue`
                    (`position`, `trackId`, `source`, `title`, `artist`, `artworkUrl`,
                     `streamUrl`, `durationMs`, `genre`, `transcodingUrl`, `isrc`, `previewSeconds`)
                VALUES
                    (0, 'deezer:1', 'DEEZER', 'Deezer track', 'Artist', NULL,
                     'https://cdns-preview.deezer.com/1.mp3', 30000, NULL,
                     'https://www.deezer.com/track/1', NULL, 30),
                    (1, 'apple:2', 'APPLE', 'Apple track', 'Artist', NULL,
                     'https://audio.mzstatic.com/2.m4a', 30000, NULL,
                     'https://music.apple.com/2', NULL, 30),
                    (2, 'sc:3', 'SOUNDCLOUD', 'SC track', 'Artist', NULL,
                     'https://api.soundcloud.com/3.mp3', 60000, NULL,
                     'https://soundcloud.com/a/3', NULL, NULL)
                """.trimIndent(),
            )
            it.execSQL(
                """
                INSERT INTO `search_cache` (`query`, `source`, `jsonResponse`, `timestamp`)
                VALUES
                    ('deezer', 'DEEZER', '{"data":[]}', 1000),
                    ('apple', 'APPLE', '{"data":[]}', 1000),
                    ('spotify', 'SPOTIFY', '{"data":[]}', 1000)
                """.trimIndent(),
            )
            it.execSQL(
                """
                INSERT INTO `audio_cache` (`trackId`, `filePath`, `sizeBytes`, `cachedAt`, `lastAccessAt`)
                VALUES
                    ('deezer:1', '/tmp/1', 100, 1, 1),
                    ('apple:2', '/tmp/2', 100, 1, 1),
                    ('sc:3', '/tmp/3', 100, 1, 1)
                """.trimIndent(),
            )
        }

        MIGRATION_14_15.migrate(db)

        assertNullColumn(db, "SELECT `streamUrl` FROM `queue` WHERE `position` = 0")
        assertNullColumn(db, "SELECT `streamUrl` FROM `queue` WHERE `position` = 1")
        assertNullColumn(db, "SELECT `previewSeconds` FROM `queue` WHERE `position` = 0")
        assertNullColumn(db, "SELECT `previewSeconds` FROM `queue` WHERE `position` = 1")
        assertEquals(
            "https://api.soundcloud.com/3.mp3",
            queryString(db, "SELECT `streamUrl` FROM `queue` WHERE `position` = 2"),
        )
        assertEquals(0, countWhere(db, "search_cache", "source IN ('DEEZER', 'APPLE')"))
        assertEquals(1, countWhere(db, "search_cache", "source = 'SPOTIFY'"))
        assertEquals(
            0,
            countWhere(db, "audio_cache", "trackId LIKE 'deezer:%' OR trackId LIKE 'apple:%'"),
        )
        assertEquals(1, countWhere(db, "audio_cache", "trackId = 'sc:3'"))
    }

    @Test
    fun `migration 13 to 14 creates audio cache table`() {
        val db = openLegacyDatabase("m-13-14.db", 13) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
        }

        MIGRATION_13_14.migrate(db)

        assertTrue(hasTable(db, "audio_cache"))
        assertEquals(
            setOf("trackId", "filePath", "sizeBytes", "cachedAt", "lastAccessAt"),
            tableColumns(db, "audio_cache"),
        )
    }

    @Test
    fun `migration 12 to 13 creates queue table`() {
        val db = openLegacyDatabase("m-12-13.db", 12) {
            it.execSQL(LIKED_TRACKS_V4_DDL)
        }

        MIGRATION_12_13.migrate(db)

        assertTrue(hasTable(db, "queue"))
        assertEquals(
            setOf(
                "position",
                "trackId",
                "source",
                "title",
                "artist",
                "artworkUrl",
                "streamUrl",
                "durationMs",
                "genre",
                "transcodingUrl",
                "isrc",
                "previewSeconds",
            ),
            tableColumns(db, "queue"),
        )
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

    /** Строковое значение первого столбца или null. */
    private fun queryString(db: SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Проверяет, что первый столбец первой строки равен SQL NULL. */
    private fun assertNullColumn(db: SupportSQLiteDatabase, sql: String) {
        db.query(sql).use { cursor ->
            assertTrue("Запрос не вернул строку: $sql", cursor.moveToFirst())
            assertTrue("Ожидался NULL: $sql", cursor.isNull(0))
        }
    }

    /** Число строк, удовлетворяющих условию WHERE. */
    private fun countWhere(db: SupportSQLiteDatabase, table: String, where: String): Int =
        db.query("SELECT COUNT(*) FROM `$table` WHERE $where").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
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
        val relative = "schemas/com.facet.music.data.local.AppDatabase/$name"
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

        /** queue состояния v13 — схема не меняется в 14 и 15. */
        val QUEUE_V13_DDL = """
            CREATE TABLE IF NOT EXISTS `queue` (
                `position` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `streamUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `genre` TEXT,
                `transcodingUrl` TEXT,
                `isrc` TEXT,
                `previewSeconds` INTEGER,
                PRIMARY KEY(`position`)
            )
        """.trimIndent()

        val SEARCH_CACHE_V10_DDL = """
            CREATE TABLE IF NOT EXISTS `search_cache` (
                `query` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `jsonResponse` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`query`)
            )
        """.trimIndent()

        val CHART_CACHE_V1_DDL = """
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
        """.trimIndent()

        val PLAYLIST_TRACKS_V12_DDL = """
            CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                `playlistId` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artworkUrl` TEXT,
                `durationMs` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `transcodingUrl` TEXT,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `trackId`)
            )
        """.trimIndent()

        val AUDIO_CACHE_V14_DDL = """
            CREATE TABLE IF NOT EXISTS `audio_cache` (
                `trackId` TEXT NOT NULL,
                `filePath` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `cachedAt` INTEGER NOT NULL,
                `lastAccessAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
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
