package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.data.local.dao.PlaylistDao
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlaylistRepository
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

/**
 * Плейлисты на настоящем Room в памяти: проверяем маппинг entity ↔ Track,
 * уникальность пары (плейлист, трек) и каскадное удаление внешнего ключа.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var repository: PlaylistRepository

    private val track = Track(
        id = "sc:track-1",
        source = Source.SOUNDCLOUD,
        title = "Cachalot",
        artist = "Deep Sea",
        artworkUrl = "https://img.example/cachalot.jpg",
        streamUrl = null,
        durationMs = 184_000L,
        genre = "Electronic",
        transcodingUrl = "https://soundcloud.com/deep-sea/cachalot",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.playlistDao()
        repository = PlaylistRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createPlaylist возвращает id и плейлист с нулём треков`() = runBlocking {
        val id = repository.createPlaylist("  Дорога  ")

        assertTrue(id > 0)
        val playlists = repository.observePlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Дорога", playlists.single().name)
        assertEquals(0, playlists.single().trackCount)
    }

    @Test
    fun `addTrack сохраняет денормализованные поля трека`() = runBlocking {
        val id = repository.createPlaylist("Дорога")

        repository.addTrack(id, track)

        val stored = repository.observeTracks(id).first().single()
        assertEquals(track.id, stored.id)
        assertEquals(track.source, stored.source)
        assertEquals(track.title, stored.title)
        assertEquals(track.artist, stored.artist)
        assertEquals(track.artworkUrl, stored.artworkUrl)
        assertEquals(track.durationMs, stored.durationMs)
        assertEquals(track.transcodingUrl, stored.transcodingUrl)
        // Поток не сохраняем: его резолвит плеер по источнику и transcodingUrl.
        assertNull(stored.streamUrl)
        assertNull(stored.genre)
    }

    @Test
    fun `повторное добавление трека не создаёт дубль`() = runBlocking {
        val id = repository.createPlaylist("Дорога")

        repository.addTrack(id, track)
        repository.addTrack(id, track)

        assertEquals(1, repository.observeTracks(id).first().size)
        assertEquals(1, dao.count(id))
    }

    @Test
    fun `observePlaylist отдаёт имя и число треков`() = runBlocking {
        val id = repository.createPlaylist("Дорога")
        repository.addTrack(id, track)
        repository.addTrack(id, track.copy(id = "sc:track-2", title = "Second"))

        val playlist = repository.observePlaylist(id).first()

        assertEquals("Дорога", playlist?.name)
        assertEquals(2, playlist?.trackCount)
    }

    @Test
    fun `removeTrack удаляет только выбранный трек`() = runBlocking {
        val id = repository.createPlaylist("Дорога")
        repository.addTrack(id, track)
        repository.addTrack(id, track.copy(id = "sc:track-2", title = "Second"))

        repository.removeTrack(id, track.id)

        assertEquals(listOf("sc:track-2"), repository.observeTracks(id).first().map { it.id })
        assertEquals(1, dao.count(id))
    }

    @Test
    fun `deletePlaylist каскадно удаляет свои треки`() = runBlocking {
        val first = repository.createPlaylist("Первый")
        val second = repository.createPlaylist("Второй")
        repository.addTrack(first, track)
        repository.addTrack(second, track.copy(id = "sc:track-2"))

        repository.deletePlaylist(first)

        assertEquals(1, repository.observePlaylists().first().size)
        assertTrue(repository.observeTracks(first).first().isEmpty())
        // Второй плейлист не тронут, а строка удалённого ушла каскадом.
        assertEquals(1, dao.countAllTracks())
        assertEquals(1, dao.count(second))
    }

    @Test
    fun `rename меняет имя, не трогая треки`() = runBlocking {
        val id = repository.createPlaylist("Старое имя")
        repository.addTrack(id, track)

        repository.rename(id, "Новое имя")

        assertEquals("Новое имя", repository.observePlaylist(id).first()?.name)
        assertEquals(1, dao.count(id))
    }
}
