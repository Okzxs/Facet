package com.my.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.local.entity.toLikedEntity
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LikedTracksRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LikedTracksRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LikedTracksRepositoryImpl(database.likedTracksDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `like and unlike emit reactive updates`() = runBlocking {
        val updates = Channel<List<Track>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            repository.getLikedTracks().collect { updates.send(it) }
        }

        try {
            assertTrue(withTimeout(5_000) { updates.receive() }.isEmpty())

            repository.likeTrack(sampleTrack)

            val afterLike = withTimeout(5_000) { updates.receive() }
            assertEquals(listOf(sampleTrack.id), afterLike.map { it.id })
            assertTrue(afterLike.single().liked)
            assertTrue(repository.isLiked(sampleTrack.id).first())

            repository.unlikeTrack(sampleTrack.id)

            val afterUnlike = withTimeout(5_000) { updates.receive() }
            assertTrue(afterUnlike.isEmpty())
            assertFalse(repository.isLiked(sampleTrack.id).first())
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `toggle like returns new state and stores one row`() = runBlocking {
        assertTrue(repository.toggleLike(sampleTrack))
        assertTrue(repository.isLiked(sampleTrack.id).first())
        assertEquals(listOf(sampleTrack.id), repository.getLikedTracks().first().map { it.id })

        assertFalse(repository.toggleLike(sampleTrack))
        assertFalse(repository.isLiked(sampleTrack.id).first())
        assertTrue(repository.getLikedTracks().first().isEmpty())
    }

    @Test
    fun `liked tracks are ordered by likedAt descending`() = runBlocking {
        val older = track("old").toLikedEntity(likedAt = 1_000L)
        val newer = track("new").toLikedEntity(likedAt = 2_000L)
        database.likedTracksDao().insertLikedTrack(older)
        database.likedTracksDao().insertLikedTrack(newer)

        val liked = repository.getLikedTracks().first()

        assertEquals(listOf("new", "old"), liked.map { it.id })
        assertTrue(liked.all { it.liked })
    }

    private val sampleTrack = track("liked-1", genre = "Rock")

    private fun track(id: String, genre: String? = null) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = "https://example.com/$id.jpg",
        streamUrl = null,
        durationMs = 60_000L,
        genre = genre,
        transcodingUrl = "https://soundcloud.com/test/$id",
    )
}
