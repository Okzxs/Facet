package com.my.music.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.local.dao.EmbeddingDao
import com.my.music.data.local.embedding.toBlob
import com.my.music.data.local.embedding.toFloatArrayOrNull
import com.my.music.data.local.entity.AudioEmbeddingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmbeddingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: EmbeddingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.embeddingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `stores replaces and deletes embedding blob`() = runBlocking {
        val firstVector = floatArrayOf(0.1f, 0.2f, 0.3f)
        dao.upsert(
            AudioEmbeddingEntity(
                trackId = "track-1",
                vector = firstVector.toBlob(),
                createdAt = 1_000L,
            ),
        )

        val first = requireNotNull(dao.getById("track-1"))
        assertEquals(1_000L, first.createdAt)
        assertEquals(firstVector.size * Float.SIZE_BYTES, first.vector.size)
        assertArrayEquals(
            firstVector,
            requireNotNull(first.vector.toFloatArrayOrNull()),
            1e-6f,
        )

        val replacement = floatArrayOf(-0.5f, 0.5f)
        dao.upsert(
            AudioEmbeddingEntity(
                trackId = "track-1",
                vector = replacement.toBlob(),
                createdAt = 2_000L,
            ),
        )

        assertEquals(1, dao.getAll().size)
        val replaced = requireNotNull(dao.getById("track-1"))
        assertEquals(2_000L, replaced.createdAt)
        assertArrayEquals(
            replacement,
            requireNotNull(replaced.vector.toFloatArrayOrNull()),
            1e-6f,
        )

        dao.delete("track-1")
        assertNull(dao.getById("track-1"))
    }

    @Test
    fun `returns only requested embeddings by ids`() = runBlocking {
        dao.upsert(AudioEmbeddingEntity("a", floatArrayOf(1f).toBlob(), 1L))
        dao.upsert(AudioEmbeddingEntity("b", floatArrayOf(2f).toBlob(), 2L))
        dao.upsert(AudioEmbeddingEntity("c", floatArrayOf(3f).toBlob(), 3L))

        val result = dao.getByIds(listOf("a", "c", "missing"))
            .associateBy { it.trackId }

        assertEquals(setOf("a", "c"), result.keys)
        assertEquals(1L, result.getValue("a").createdAt)
        assertEquals(3L, result.getValue("c").createdAt)
    }
}
