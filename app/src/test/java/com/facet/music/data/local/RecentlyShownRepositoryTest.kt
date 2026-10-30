package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentlyShownRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RecentlyShownRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecentlyShownRepositoryImpl(database.recentlyShownDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `returns only tracks inside ttl and cleans expired rows`() = runBlocking {
        repository.markShown(
            trackIds = listOf("fresh"),
            now = 10_000L,
            ttlMs = 1_000L,
        )
        repository.markShown(
            trackIds = listOf("old"),
            now = 5_000L,
            ttlMs = 1_000L,
        )

        assertEquals(
            setOf("fresh"),
            repository.getRecentlyShownIds(now = 10_000L, ttlMs = 1_000L),
        )

        repository.markShown(
            trackIds = emptyList(),
            now = 20_000L,
            ttlMs = 1_000L,
        )

        assertEquals(
            emptySet<String>(),
            repository.getRecentlyShownIds(now = 20_000L, ttlMs = 1_000L),
        )
    }

    @Test
    fun `refreshes timestamp when track is shown again`() = runBlocking {
        repository.markShown(
            trackIds = listOf("track"),
            now = 1_000L,
            ttlMs = 5_000L,
        )
        repository.markShown(
            trackIds = listOf("track"),
            now = 8_000L,
            ttlMs = 5_000L,
        )

        assertEquals(
            setOf("track"),
            repository.getRecentlyShownIds(now = 9_000L, ttlMs = 5_000L),
        )
    }
}
