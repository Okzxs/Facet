package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.data.local.dao.AudioCacheDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Кэш прослушанного: файл и запись живут вместе, пропавший файл вычищает
 * запись, а LRU-очистка не даёт кэшу выйти за лимит.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioCacheRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AudioCacheDao
    private lateinit var repository: AudioCacheRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.audioCacheDao()
        repository = AudioCacheRepositoryImpl(context, dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun writeFile(trackId: String, bytes: Int): java.io.File {
        val file = repository.fileForWrite(trackId)
        file.writeBytes(ByteArray(bytes) { it.toByte() })
        return file
    }

    @Test
    fun `put registers file and fileFor returns it`() = runBlocking {
        val file = writeFile("track-a", 100)
        repository.put("track-a", file)

        assertEquals(file.absolutePath, repository.fileFor("track-a")?.absolutePath)
        assertEquals(100L, repository.totalBytes())
    }

    @Test
    fun `missing file removes stale cache row`() = runBlocking {
        val file = writeFile("track-b", 50)
        repository.put("track-b", file)
        file.delete()

        assertNull(repository.fileFor("track-b"))
        assertEquals(0L, repository.totalBytes())
    }

    @Test
    fun `clear removes rows and files`() = runBlocking {
        val first = writeFile("track-c", 40)
        val second = writeFile("track-d", 60)
        repository.put("track-c", first)
        repository.put("track-d", second)

        repository.clear()

        assertEquals(0L, repository.totalBytes())
        assertTrue(!first.exists())
        assertTrue(!second.exists())
    }

    @Test
    fun `enforceLimit evicts until size fits`() = runBlocking {
        val first = writeFile("track-e", 100)
        val second = writeFile("track-f", 100)
        val third = writeFile("track-g", 100)
        repository.put("track-e", first)
        repository.put("track-f", second)
        repository.put("track-g", third)

        repository.enforceLimit(maxBytes = 250)

        assertTrue("кэш должен вписаться в лимит", repository.totalBytes() <= 250)
        assertNotNull(repository.fileFor("track-g"))
    }
}
