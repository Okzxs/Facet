package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.data.local.dao.QueueDao
import com.facet.music.data.local.entity.QueueEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Очередь на настоящем Room в памяти: маппинг entity ↔ Track, перезапись
 * целиком и восстановление позиции текущего трека.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueueRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: QueueDao
    private lateinit var repository: QueueRepositoryImpl

    private fun track(id: String, title: String) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = "Artist",
        artworkUrl = "https://img.example/$id.jpg",
        streamUrl = null,
        durationMs = 180_000L,
        genre = "Electronic",
        transcodingUrl = "https://soundcloud.com/artist/$id",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.queueDao()
        repository = QueueRepositoryImpl(dao, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saved queue survives reload with current index and position`() = runBlocking {
        repository.save(
            listOf(track("a", "A"), track("b", "B"), track("c", "C")),
            currentIndex = 1,
            positionMs = 42_500L,
        )

        val snapshot = repository.load()

        assertEquals(listOf("a", "b", "c"), snapshot?.tracks?.map { it.id })
        assertEquals("B", snapshot?.tracks?.get(1)?.title)
        assertEquals(1, snapshot?.currentIndex)
        assertEquals(42_500L, snapshot?.positionMs)
    }

    @Test
    fun `save replaces the whole queue`() = runBlocking {
        repository.save(listOf(track("a", "A"), track("b", "B")), currentIndex = 0)
        repository.save(listOf(track("c", "C")), currentIndex = 0)

        assertEquals(listOf("c"), repository.load()?.tracks?.map { it.id })
    }

    @Test
    fun `clear removes saved queue`() = runBlocking {
        repository.save(listOf(track("a", "A")), currentIndex = 0)

        repository.clear()

        assertNull(repository.load())
        assertEquals(0, dao.getAll().size)
    }

    @Test
    fun `broken source rows are skipped`() = runBlocking {
        dao.insertAll(
            listOf(
                QueueEntity(
                    position = 0,
                    trackId = "broken",
                    source = "NOT_A_SOURCE",
                    title = "Broken",
                    artist = "Artist",
                    artworkUrl = null,
                    streamUrl = null,
                    durationMs = 1_000L,
                    genre = null,
                    transcodingUrl = null,
                    isrc = null,
                    previewSeconds = null,
                ),
                QueueEntity(
                    position = 1,
                    trackId = "ok",
                    source = "YOUTUBE",
                    title = "Ok",
                    artist = "Artist",
                    artworkUrl = null,
                    streamUrl = null,
                    durationMs = 2_000L,
                    genre = null,
                    transcodingUrl = null,
                    isrc = null,
                    previewSeconds = null,
                ),
            ),
        )

        val snapshot = repository.load()

        assertEquals(listOf("ok"), snapshot?.tracks?.map { it.id })
        assertEquals(0, snapshot?.currentIndex)
    }
}
