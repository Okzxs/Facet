package com.my.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.local.dao.SearchCacheDao
import com.my.music.data.local.entity.SearchCacheEntity
import com.my.music.data.model.Source
import com.my.music.data.repository.SearchCacheRepository
import com.my.music.data.repository.SearchCacheRepository.Companion.DEFAULT_TTL_MS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchCacheRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: SearchCacheDao
    private lateinit var repository: SearchCacheRepository

    private val payload = """{"data":[{"id":1,"title":"Track"}]}"""

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.searchCacheDao()
        repository = SearchCacheRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `put и get возвращают сохранённый json`() = runBlocking {
        repository.put("muse", Source.DEEZER, payload)

        assertEquals(payload, repository.get("muse", Source.DEEZER))
    }

    @Test
    fun `просроченная запись удаляется и не отдаётся`() = runBlocking {
        dao.upsert(
            SearchCacheEntity(
                query = "muse",
                source = Source.DEEZER.name,
                jsonResponse = payload,
                timestamp = 1_000L,
            ),
        )

        assertNull(repository.get("muse", Source.DEEZER))
        assertNull(dao.get("muse", Source.DEEZER.name))
    }

    @Test
    fun `запись другого источника не отдаётся`() = runBlocking {
        repository.put("muse", Source.DEEZER, payload)

        assertNull(repository.get("muse", Source.APPLE))
    }

    @Test
    fun `свежая запись живёт в пределах ttl`() = runBlocking {
        val now = System.currentTimeMillis()
        dao.upsert(
            SearchCacheEntity(
                query = "muse",
                source = Source.DEEZER.name,
                jsonResponse = payload,
                timestamp = now - 5_000L,
            ),
        )

        assertNotNull(repository.get("muse", Source.DEEZER, ttlMs = 60_000L))
        // С этим TTL запись уже просрочена и должна исчезнуть.
        assertNull(repository.get("muse", Source.DEEZER, ttlMs = 1_000L))
    }

    @Test
    fun `put вычищает записи старше суток`() = runBlocking {
        dao.upsert(
            SearchCacheEntity(
                query = "old",
                source = Source.APPLE.name,
                jsonResponse = payload,
                timestamp = 1L,
            ),
        )

        repository.put("new", Source.DEEZER, payload)

        assertNull(dao.get("old", Source.APPLE.name))
        assertEquals(payload, dao.get("new", Source.DEEZER.name)?.jsonResponse)
    }

    @Test
    fun `clear удаляет весь кэш`() = runBlocking {
        repository.put("muse", Source.DEEZER, payload)
        repository.put("nirvana", Source.APPLE, payload)

        repository.clear()

        assertNull(repository.get("muse", Source.DEEZER))
        assertNull(repository.get("nirvana", Source.APPLE))
    }

    @Test
    fun `ttl по умолчанию равен суткам`() {
        assertEquals(24L * 60 * 60 * 1_000, DEFAULT_TTL_MS)
    }
}
