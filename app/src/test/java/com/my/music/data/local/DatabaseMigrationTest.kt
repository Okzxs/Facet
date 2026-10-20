package com.my.music.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.local.entity.ChartCacheEntity
import com.my.music.data.local.entity.GenreWeightEntity
import com.my.music.data.local.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationTest {

    private val databaseName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migration from version 1 to 5 preserves likes and creates wave tables`() = runBlocking {
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

            assertEquals(5, database.openHelper.writableDatabase.version)

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
        } finally {
            database.close()
        }
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
