package com.facet.music.data.source.wave

import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.SeedArtist
import com.facet.music.core.wave.TrackSeed
import com.facet.music.core.wave.WaveEngine
import com.facet.music.core.wave.WaveGeneration
import com.facet.music.core.wave.WaveHistory
import com.facet.music.core.wave.WaveHistoryStore
import com.facet.music.core.wave.WaveSessionPlan
import com.facet.music.core.wave.WaveSessionState
import com.facet.music.core.wave.WaveTasteProfile
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveRepositoryTest {

    private val engine = mockk<WaveEngine>()
    private val historyStore = mockk<WaveHistoryStore>(relaxed = true)
    private lateinit var repository: WaveRepositoryImpl

    private val track = Track(
        id = "track-1",
        source = Source.SOUNDCLOUD,
        title = "Title",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = "rock",
        transcodingUrl = "https://soundcloud.com/test/track-1",
    )

    private val plan = WaveSessionPlan(
        sessionId = "session-1",
        createdAt = 1_800_000_000_000L,
        randomSeed = 42L,
        profile = WaveTasteProfile(),
        history = WaveHistory(),
        seedArtists = listOf(SeedArtist("artist", "Artist", 1.0)),
        seedGenres = listOf("rock"),
        seedTracks = listOf(
            TrackSeed(track, 1.0, "artist|title", "artist"),
        ),
        previousSeedKeys = emptySet(),
    )

    @Before
    fun setUp() {
        coEvery { engine.startSession(any(), any()) } returns plan
        repository = WaveRepositoryImpl(
            engine = engine,
            historyStore = historyStore,
            clock = object : Clock {
                override fun nowMs(): Long = 1_800_000_000_000L
            },
        )
    }

    @Test
    fun `start session exposes engine plan and persists it`() = runBlocking {
        val session = repository.startSession(listOf("seed-id")).getOrThrow()

        assertEquals("session-1", session.sessionId)
        assertEquals(listOf("Artist"), session.seedArtists)
        assertEquals(listOf("rock"), session.seedGenres)
        assertEquals(setOf("seed-id"), session.shownTrackIds)
        assertTrue(session.radioMode.not())
    }

    @Test
    fun `next batch updates counters and shown ids`() = runBlocking {
        coEvery { engine.startSession(any(), any()) } returns plan
        coEvery { engine.nextBatch(any(), any(), any()) } returns WaveGeneration(
            tracks = listOf(track),
            origins = mapOf(CandidateOrigin.RELATED to 1),
            state = WaveSessionState(batchNumber = 1, shownTrackKeys = setOf("artist|title")),
            requestedSize = 10,
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(10).getOrThrow()

        assertEquals(listOf(track), batch.tracks)
        assertEquals(1, batch.session.batchNumber)
        assertTrue(batch.session.shownTrackIds.contains(track.id))
    }
}
