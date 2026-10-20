package com.my.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.runBlocking
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
class TasteRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TasteRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TasteRepositoryImpl(database.tasteDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `likes add weight to normalized genre and artist`() = runBlocking {
        repository.onLike(track(artist = "  Artist  ", genre = " Rock "))

        assertEquals(3.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
        assertEquals(3.0, database.tasteDao().getArtistWeight("artist") ?: 0.0, 0.0001)
    }

    @Test
    fun `finished track adds weight only above threshold`() = runBlocking {
        repository.onTrackFinished(sampleTrack, playedFraction = 0.8f)
        assertEquals(0.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)

        repository.onTrackFinished(sampleTrack, playedFraction = 0.81f)

        assertEquals(1.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
        assertEquals(1.0, database.tasteDao().getArtistWeight("artist") ?: 0.0, 0.0001)
    }

    @Test
    fun `early skip subtracts weight and long listen does not`() = runBlocking {
        repository.onSkip(sampleTrack, playedMs = 29_999L)

        assertEquals(-1.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)

        repository.onSkip(sampleTrack, playedMs = 30_000L)

        assertEquals(-1.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
    }

    @Test
    fun `unlike removes like weight and russian priority is stored`() = runBlocking {
        repository.onLike(sampleTrack)
        repository.onUnlike(sampleTrack)

        assertEquals(0.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
        assertEquals(0.0, database.tasteDao().getArtistWeight("artist") ?: 0.0, 0.0001)

        assertTrue(repository.isRussianPriority())

        repository.setRussianPriority(false)

        assertFalse(repository.isRussianPriority())
    }

    private val sampleTrack = track(artist = "Artist", genre = "Rock")

    private fun track(artist: String, genre: String?) = Track(
        id = "taste-1",
        source = Source.SOUNDCLOUD,
        title = "Title",
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = genre,
        transcodingUrl = "https://soundcloud.com/test/taste-1",
    )
}
