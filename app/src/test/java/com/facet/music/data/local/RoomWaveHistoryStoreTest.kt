package com.facet.music.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.Candidate
import com.facet.music.core.wave.CandidateOrigin
import com.facet.music.core.wave.Clock
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.core.wave.WaveSessionRecord
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.local.entity.LikedTrackEntity
import com.facet.music.data.local.entity.PlayHistoryEntity
import com.facet.music.data.local.entity.QueueEntity
import com.facet.music.data.local.entity.toLikedEntity
import com.facet.music.data.local.entity.toHistoryEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomWaveHistoryStoreTest {

    private lateinit var database: AppDatabase
    private lateinit var store: RoomWaveHistoryStore
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomWaveHistoryStore(
            waveDao = database.waveDao(),
            likedTracksDao = database.likedTracksDao(),
            playHistoryDao = database.playHistoryDao(),
            dislikedTrackDao = database.dislikedTrackDao(),
            queueDao = database.queueDao(),
            tasteDao = database.tasteDao(),
            clock = object : Clock {
                override fun nowMs(): Long = now
            },
            json = Json,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `shown tracks are stored as impressions and outcomes update latest`() = runBlocking {
        val track = track("track-1")
        store.recordShown("session-1", listOf(Candidate(track, CandidateOrigin.RELATED, "seed")))

        val key = MusicNormalizer.trackKey(track.artist, track.title)
        assertEquals(WaveOutcome.SHOWN.name, database.waveDao().latestImpression(key)?.outcome)

        store.recordOutcome("session-1", track, WaveOutcome.FINISHED, 180_000L)

        assertEquals(WaveOutcome.FINISHED.name, database.waveDao().latestImpression(key)?.outcome)
        assertEquals(180_000L, database.waveDao().latestImpression(key)?.playedMs)
    }

    @Test
    fun `three fast skips of one artist create automatic block`() = runBlocking {
        val artist = "Skip Artist"
        val tracks = (1..3).map { track("skip-$it", artist) }
        tracks.forEach {
            store.recordShown("session-1", listOf(Candidate(it, CandidateOrigin.RELATED, "seed")))
            store.recordOutcome("session-1", it, WaveOutcome.SKIPPED, 10_000L)
        }

        val history = store.loadHistory(now)
        assertTrue(history.blockedArtistKeys.contains(MusicNormalizer.normalizeArtist(artist)))
        val block = database.waveDao().blocklist().single()
        assertEquals("AUTO_SKIPS", block.reason)
        assertEquals(now + 30L * 86_400_000L, block.until)
    }

    @Test
    fun `only ten latest sessions are kept and seed keys are readable`() = runBlocking {
        repeat(12) { index ->
            store.saveSession(
                WaveSessionRecord(
                    sessionId = "s-$index",
                    createdAt = now + index,
                    randomSeed = index.toLong(),
                    mode = "NORMAL",
                    seedArtistKeys = listOf("artist-$index"),
                    seedTrackKeys = listOf("track-$index"),
                    isRadio = false,
                    radioSeed = null,
                    cursor = 1,
                ),
            )
        }

        val sessions = database.waveDao().sessions(50)
        assertEquals(10, sessions.size)
        assertEquals(setOf("track-11", "track-10"), store.previousSessionSeedKeys(2))
    }

    @Test
    fun `seed tracks include likes and finished history within window`() = runBlocking {
        val liked = track("liked", "Liked Artist")
        val finished = track("finished", "Finished Artist")
        val legacy = track("legacy", "Legacy Artist")
        val halfPlayed = track("half", "Half Artist")
        database.likedTracksDao().insertLikedTrack(
            LikedTrackEntity(
                id = liked.id,
                source = liked.source.name,
                title = liked.title,
                artist = liked.artist,
                artworkUrl = null,
                durationMs = liked.durationMs,
                genre = liked.genre,
                transcodingUrl = liked.transcodingUrl,
                likedAt = now - 2 * DAY_MS,
            ),
        )
        database.playHistoryDao().insertPlayHistory(
            PlayHistoryEntity(
                id = finished.id,
                source = finished.source.name,
                title = finished.title,
                artist = finished.artist,
                artworkUrl = null,
                durationMs = finished.durationMs,
                genre = finished.genre,
                transcodingUrl = finished.transcodingUrl,
                playedAt = now - DAY_MS,
                playedFraction = 0.9f,
            ),
        )
        database.playHistoryDao().insertPlayHistory(
            halfPlayed.toHistoryEntity(playedAt = now - DAY_MS, playedFraction = 0.5f),
        )
        database.playHistoryDao().insertPlayHistory(
            legacy.toHistoryEntity(playedAt = now - DAY_MS, playedFraction = null),
        )

        val seeds = store.seedTracks(now - 60 * DAY_MS)
        assertEquals(
            setOf("liked", "finished", "legacy"),
            seeds.map { it.track.id }.toSet(),
        )
    }

    @Test
    fun `history contains dislikes blocked artists and queue`() = runBlocking {
        val disliked = track("disliked", "Disliked Artist")
        database.dislikedTrackDao().insert(
            DislikedTrackEntity(trackId = disliked.id, dislikedAt = now),
        )
        store.blockArtist("blocked artist", now)
        database.queueDao().insertAll(
            listOf(
                QueueEntity(
                    position = 0,
                    trackId = "queued",
                    source = Source.SOUNDCLOUD.name,
                    title = "Queued song",
                    artist = "Queue Artist",
                    artworkUrl = null,
                    streamUrl = null,
                    durationMs = 180_000L,
                    genre = null,
                    transcodingUrl = null,
                    isrc = null,
                    previewSeconds = null,
                ),
            ),
        )

        val history = store.loadHistory(now)
        assertTrue(history.dislikedTrackIds.contains("disliked"))
        assertTrue(history.blockedArtistKeys.contains("blocked artist"))
        assertTrue(history.queueTrackIds.contains("queued"))
        assertTrue(
            history.queueTrackKeys.contains(
                MusicNormalizer.trackKey("Queue Artist", "Queued song"),
            ),
        )
    }

    @Test
    fun `cleanup removes ninety day old impressions only once per day`() = runBlocking {
        val track = track("old")
        now -= 100 * DAY_MS
        store.recordShown("session-1", listOf(Candidate(track, CandidateOrigin.RELATED, "seed")))
        now += 100 * DAY_MS

        store.cleanupIfNeeded(now)
        assertEquals(0, database.waveDao().impressionsSince(0L).size)

        now -= 100 * DAY_MS
        store.recordShown("session-1", listOf(Candidate(track, CandidateOrigin.RELATED, "seed")))
        now += 100 * DAY_MS
        store.cleanupIfNeeded(now)

        assertEquals(1, database.waveDao().impressionsSince(0L).size)
    }

    private fun track(id: String, artist: String = "Artist") = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = "rock",
        transcodingUrl = "https://soundcloud.com/test/$id",
    )

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
