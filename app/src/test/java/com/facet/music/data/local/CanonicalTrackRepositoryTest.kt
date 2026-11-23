package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.CanonicalTrackRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CanonicalTrackRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CanonicalTrackRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CanonicalTrackRepositoryImpl(database.canonicalTrackDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun track(
        id: String,
        source: Source,
        title: String = "One More Time",
        artist: String = "Daft Punk",
        streamUrl: String? = "https://cdn/$id.mp3",
        previewSeconds: Int? = null,
        durationMs: Long = 320_000L,
        subscriptionOnly: Boolean = false,
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = "https://art/$id.jpg",
        streamUrl = streamUrl,
        durationMs = durationMs,
        genre = null,
        transcodingUrl = "https://source/$id",
        previewSeconds = previewSeconds,
        isSubscriptionOnly = subscriptionOnly,
    )

    @Test
    fun `same track from two sources collapses into one canonical track`() = runBlocking {
        val soundCloud = track("sc:1", Source.SOUNDCLOUD)
        val youtube = track("yt:1", Source.YOUTUBE)

        val canonical = repository.canonicalize(listOf(soundCloud, youtube)).single()

        assertEquals("daft punk|one more time", canonical.key)
        assertEquals(2, canonical.sourceCount)
        assertEquals(2, canonical.variants.size)
        assertEquals(setOf(Source.SOUNDCLOUD, Source.YOUTUBE), canonical.sources.toSet())
        assertTrue(canonical.variants.all { it.confidence >= 0.7 })
    }

    @Test
    fun `different tracks stay separate`() = runBlocking {
        val canonical = repository.canonicalize(
            listOf(
                track("sc:1", Source.SOUNDCLOUD),
                track("yt:2", Source.YOUTUBE, title = "Get Lucky"),
            ),
        )

        assertEquals(2, canonical.size)
        assertTrue(canonical.all { it.sourceCount == 1 })
    }

    @Test
    fun `full stream is primary over preview and go plus`() = runBlocking {
        val preview = track("dz:1", Source.DEEZER, streamUrl = "https://preview", previewSeconds = 30)
        val goPlus = track("sc:2", Source.SOUNDCLOUD, subscriptionOnly = true)
        val full = track("yt:3", Source.YOUTUBE)

        val canonical = repository.canonicalize(listOf(preview, goPlus, full)).single()

        assertEquals("yt:3", canonical.primary.id)
        assertEquals(Source.YOUTUBE, canonical.primary.source)
    }

    @Test
    fun `recorded variants survive and are ordered by playability`() = runBlocking {
        repository.recordVariants(
            listOf(
                track("dz:1", Source.DEEZER, streamUrl = "https://preview", previewSeconds = 30),
                track("yt:1", Source.YOUTUBE),
            ),
        )

        val key = repository.canonicalKey(track("yt:1", Source.YOUTUBE))
        val variants = repository.variants(key)

        assertEquals(2, variants.size)
        assertEquals("yt:1", variants.first().id)

        val best = repository.bestForPlayback(key)
        assertEquals("yt:1", best?.id)

        val resolved = repository.resolveByTrackId("dz:1")
        assertNotNull(resolved)
        assertEquals(2, resolved?.variants?.size)
        assertEquals("yt:1", resolved?.primary?.id)
    }

    @Test
    fun `fallback returns another source for the same canonical track`() = runBlocking {
        val soundCloud = track("sc:1", Source.SOUNDCLOUD)
        repository.recordVariants(
            listOf(
                soundCloud,
                track("yt:1", Source.YOUTUBE),
            ),
        )

        val fallback = repository.fallbackFor(soundCloud)

        assertEquals("yt:1", fallback?.id)
        assertNull(repository.fallbackFor(track("sc:missing", Source.SOUNDCLOUD, title = "Unknown")))
    }

    @Test
    fun `near durations get higher confidence than distant ones`() = runBlocking {
        val primary = track("sc:1", Source.SOUNDCLOUD, durationMs = 320_000L)
        val near = track("yt:1", Source.YOUTUBE, durationMs = 321_000L)
        val far = track("yt:2", Source.YOUTUBE, durationMs = 180_000L)

        val canonical = repository.canonicalize(listOf(primary, near, far)).single()
        val byId = canonical.variants.associateBy { it.track.id }

        assertTrue(byId.getValue("yt:1").confidence > byId.getValue("yt:2").confidence)
    }

    @Test
    fun `blank titles are ignored`() = runBlocking {
        assertTrue(repository.canonicalize(listOf(track("sc:1", Source.SOUNDCLOUD, title = ""))).isEmpty())
    }
}
