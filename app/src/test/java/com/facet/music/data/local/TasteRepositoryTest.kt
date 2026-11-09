package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.core.wave.Clock
import com.facet.music.data.local.entity.GenreWeightEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.TasteContext
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
class TasteRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TasteRepositoryImpl
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TasteRepositoryImpl(
            tasteDao = database.tasteDao(),
            clock = object : Clock {
                override fun nowMs(): Long = now
            },
        )
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
    fun `skip in wave costs more than skip in own playlist`() = runBlocking {
        repository.onSkip(sampleTrack, playedMs = 29_999L, context = TasteContext.WAVE)
        repository.onSkip(sampleTrack, playedMs = 29_999L, context = TasteContext.NORMAL)

        assertEquals(-1.3, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)

        repository.onSkip(sampleTrack, playedMs = 30_000L, context = TasteContext.WAVE)
        assertEquals(-1.3, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
    }

    @Test
    fun `dislike subtracts four and unlike removes like`() = runBlocking {
        repository.onLike(sampleTrack)
        repository.onDislike(sampleTrack)

        assertEquals(-1.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)

        repository.onLike(sampleTrack)
        repository.onUnlike(sampleTrack)

        assertEquals(-1.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
    }

    @Test
    fun `effective weight halves in thirty days on read and write`() = runBlocking {
        val old = now - 30L * 86_400_000L
        database.tasteDao().upsertGenreWeight(
            GenreWeightEntity(genre = "rock", weight = 4.0, updatedAt = old),
        )

        val top = repository.getTopGenres(5)
        assertEquals("rock", top.first().value)
        assertEquals(2.0, top.first().weight, 0.0001)

        repository.onLike(sampleTrack)
        assertEquals(5.0, database.tasteDao().getGenreWeight("rock") ?: 0.0, 0.0001)
    }

    @Test
    fun `top artists and genres are sorted by decayed weight`() = runBlocking {
        val old = now - 60L * 86_400_000L
        database.tasteDao().upsertGenreWeight(
            GenreWeightEntity(genre = "old", weight = 4.0, updatedAt = old),
        )
        database.tasteDao().upsertGenreWeight(
            GenreWeightEntity(genre = "fresh", weight = 2.0, updatedAt = now),
        )

        val top = repository.getTopGenres(5)
        assertEquals(listOf("fresh", "old"), top.map { it.value })
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
