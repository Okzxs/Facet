package com.facet.music.core.wave

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WaveEngineTest {

    private val now = 1_800_000_000_000L

    private val profile = WaveTasteProfile(
        artists = mapOf(
            "seed artist" to 1.0,
            "second seed" to 0.6,
        ),
        genres = mapOf("rock" to 1.0),
    )

    private val seedTrack = track("seed", "Seed Artist", "Seed Song")

    private val plan = WaveSessionPlan(
        sessionId = "session-1",
        createdAt = now,
        randomSeed = 42L,
        profile = profile,
        history = WaveHistory(),
        seedArtists = listOf(
            SeedArtist("seed artist", "Seed Artist", 1.0),
            SeedArtist("second seed", "Second Seed", 0.6),
        ),
        seedGenres = listOf("rock"),
        seedTracks = listOf(
            TrackSeed(
                track = seedTrack,
                weight = 1.0,
                trackKey = MusicNormalizer.trackKey(seedTrack.artist, seedTrack.title),
                artistKey = "seed artist",
            ),
        ),
        previousSeedKeys = emptySet(),
    )

    @Test
    fun `hard filters remove disliked blocked recent queued long and marked tracks`() = runBlocking {
        val good = (1..30).map { track("good-$it", "Artist $it") }
        val badDisliked = track("bad-disliked", "Artist D1")
        val badBlocked = track("bad-blocked", "Blocked Artist")
        val badShown = track("bad-shown", "Artist S1")
        val badPlayed = track("bad-played", "Artist P1")
        val badQueue = track("bad-queue", "Artist Q1")
        val badLong = track("bad-long", "Artist L1", durationMs = 900_000L)
        val badShort = track("bad-short", "Artist L2", durationMs = 30_000L)
        val badMarker = track("bad-marker", "Artist M1", title = "Song (8D Audio)")
        val badSubscription = track("bad-sub", "Artist G1", subscription = true)

        val history = WaveHistory(
            impressions = listOf(
                impression(badShown, now - 2 * DAY_MS),
            ),
            plays = listOf(
                PlayedTrack(badPlayed, now - 2 * DAY_MS, 1f, false),
            ),
            dislikedTrackIds = setOf(badDisliked.id),
            blockedArtistKeys = setOf("blocked artist"),
            queueTrackIds = setOf(badQueue.id),
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = listOf(
                badDisliked, badBlocked, badShown, badPlayed, badQueue,
                badLong, badShort, badMarker, badSubscription,
            ).map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                good.map { Candidate(it, CandidateOrigin.RELATED, it.id) },
        )

        val result = engine.nextBatch(plan.copy(history = history), WaveSessionState(), 20)

        val ids = result.tracks.map { it.id }.toSet()
        listOf(
            badDisliked, badBlocked, badShown, badPlayed, badQueue,
            badLong, badShort, badMarker, badSubscription,
        ).forEach { assertFalse(it.id, it.id in ids) }
        assertTrue(result.tracks.size >= 10)
    }

    @Test
    fun `first track is a confident related track and never familiar`() = runBlocking {
        val related = (1..15).map { track("rel-$it", "Related $it") }
        val familiar = (1..4).map { track("old-$it", "Old $it") }
        val history = WaveHistory(
            plays = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, false) },
            familiarTracks = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, true) },
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = related.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                familiar.map { Candidate(it, CandidateOrigin.FAMILIAR, it.id) },
        )

        val result = engine.nextBatch(plan.copy(history = history), WaveSessionState(), 20)

        assertTrue(result.tracks.first().id.startsWith("rel-"))
        assertEquals(1, result.origins[CandidateOrigin.FAMILIAR] ?: 0)
    }

    @Test
    fun `familiar quota is ten percent and never two in a row`() = runBlocking {
        val core = (1..20).map { track("core-$it", "Artist $it") }
        val familiar = (1..5).map { track("old-$it", "Old $it") }
        val history = WaveHistory(
            plays = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, false) },
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                familiar.map { Candidate(it, CandidateOrigin.FAMILIAR, it.id) },
        )

        val result = engine.nextBatch(plan.copy(history = history), WaveSessionState(), 20)

        val familiarCount = result.tracks.count { it.id.startsWith("old-") }
        assertTrue("familiar=$familiarCount", familiarCount <= 2)
        val types = result.tracks.map { it.id.startsWith("old-") }
        types.zipWithNext().forEach { (left, right) ->
            assertFalse("Два знакомых подряд", left && right)
        }
    }

    @Test
    fun `exploration share stays between fifteen and twenty five percent`() = runBlocking {
        val core = (1..20).map { track("core-$it", "Artist $it") }
        val tags = (1..6).map { track("tag-$it", "Tag Artist $it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                tags.map { Candidate(it, CandidateOrigin.TAG, it.id) },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 20)

        val exploration = result.tracks.count { it.id.startsWith("tag-") }
        assertTrue("exploration=$exploration", exploration in 3..5)
        assertEquals(20, result.tracks.size)
    }

    @Test
    fun `discovery mood uses thirty five percent exploration`() = runBlocking {
        val core = (1..20).map { track("core-$it", "Artist $it") }
        val tags = (1..12).map { track("tag-$it", "Tag Artist $it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                tags.map { Candidate(it, CandidateOrigin.TAG, it.id) },
        )

        val result = engine.nextBatch(
            plan.copy(mood = WaveMood.DISCOVERY),
            WaveSessionState(),
            20,
        )

        val exploration = result.tracks.count { it.id.startsWith("tag-") }
        assertTrue("exploration=$exploration", exploration in 6..8)
    }

    @Test
    fun `radio excludes familiar and caps the seed artist`() = runBlocking {
        val focus = (1..10).map { track("focus-$it", "Focus Artist") }
        val other = (1..15).map { track("other-$it", "Other $it") }
        val familiar = (1..4).map { track("old-$it", "Old $it") }
        val history = WaveHistory(
            plays = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, false) },
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = focus.map { Candidate(it, CandidateOrigin.RELATED, "focus") } +
                other.map { Candidate(it, CandidateOrigin.SIMILAR_ARTIST, "other") } +
                familiar.map { Candidate(it, CandidateOrigin.FAMILIAR, "old") },
        )

        val result = engine.nextBatch(
            plan.copy(
                history = history,
                isRadio = true,
                radioArtistKey = "focus artist",
                seedArtists = listOf(SeedArtist("focus artist", "Focus Artist", 1.0)),
            ),
            WaveSessionState(),
            20,
        )

        assertEquals(0, result.origins[CandidateOrigin.FAMILIAR] ?: 0)
        val focusCount = result.tracks.count { it.id.startsWith("focus-") }
        assertTrue("focus=$focusCount", focusCount <= 5)
        val focusPositions = result.tracks.mapIndexedNotNull { index, track ->
            index.takeIf { track.id.startsWith("focus-") }
        }
        focusPositions.zipWithNext().forEach { (left, right) ->
            assertTrue("focus positions $left/$right", right - left >= 4)
        }
    }

    @Test
    fun `one artist does not appear more than twice and not twice in five`() = runBlocking {
        val sameArtist = (1..10).map { track("same-$it", "One Artist") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = sameArtist.map { Candidate(it, CandidateOrigin.RELATED, "seed") },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 20)

        assertTrue(result.isShort)
        assertTrue(result.tracks.size <= 2)
    }

    @Test
    fun `no likes or history fallback when there are only familiar candidates`() = runBlocking {
        val familiar = (1..8).map { track("old-$it", "Old $it") }
        val history = WaveHistory(
            plays = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, false) },
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = familiar.map { Candidate(it, CandidateOrigin.FAMILIAR, "seed") },
        )

        val result = engine.nextBatch(plan.copy(history = history), WaveSessionState(), 20)

        assertTrue(result.isShort)
        assertTrue(result.tracks.size <= 2)
    }

    @Test
    fun `duplicate track key prefers a full stream source`() = runBlocking {
        val youtube = track(
            "yt-1",
            "Artist",
            "Song",
            source = Source.YOUTUBE,
        )
        val spotify = track(
            "sp-1",
            "Artist",
            "Song",
            source = Source.SPOTIFY,
        )
        val core = (1..10).map { track("core-$it", "Artist $it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = listOf(
                Candidate(spotify, CandidateOrigin.SIMILAR_ARTIST, "sp"),
                Candidate(youtube, CandidateOrigin.SIMILAR_ARTIST, "yt"),
            ) + core.map { Candidate(it, CandidateOrigin.RELATED, it.id) },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 20)

        assertTrue(result.tracks.any { it.id == "yt-1" })
        assertFalse(result.tracks.any { it.id == "sp-1" })
    }

    @Test
    fun `candidate details are stored for every selected track`() = runBlocking {
        val core = (1..10).map { track("core-$it", "Artist $it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 10)

        assertEquals(result.tracks.size, result.details.size)
        assertTrue(result.details.values.all { it.origin == CandidateOrigin.RELATED })
    }

    @Test
    fun `liked marker allows tracks that would otherwise be filtered`() = runBlocking {
        val slowed = track("slow-1", "Artist", "Song (Slowed)")
        val core = (1..10).map { track("core-$it", "Artist $it") }
        val likedProfile = profile.copy(likedMarkers = setOf("slowed"))
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            profile = likedProfile,
            candidates = listOf(Candidate(slowed, CandidateOrigin.RELATED, "slow")) +
                core.map { Candidate(it, CandidateOrigin.RELATED, it.id) },
        )

        val result = engine.nextBatch(
            plan.copy(profile = likedProfile),
            WaveSessionState(),
            20,
        )

        assertTrue(result.tracks.any { it.id == "slow-1" })
    }

    @Test
    fun `never puts more than three tracks of one seed in a row`() = runBlocking {
        val seedA = (1..10).map { track("a-$it", "Artist A-$it") }
        val seedB = (1..10).map { track("b-$it", "Artist B-$it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = seedA.map { Candidate(it, CandidateOrigin.RELATED, "seed-a") } +
                seedB.map { Candidate(it, CandidateOrigin.RELATED, "seed-b") },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 20)
        val seeds = result.tracks.map { result.details[MusicNormalizer.trackKey(it.artist, it.title)]?.seedKey }
        var run = 0
        var prev: String? = null
        seeds.forEach { seed ->
            run = if (seed != null && seed == prev) run + 1 else 1
            prev = seed
            assertTrue("seed run=$run", run <= 3)
        }
    }

    @Test
    fun `artist window five keeps same artist at least five tracks apart`() = runBlocking {
        val repeat = (1..3).map { track("rep-$it", "Repeat Artist") }
        val others = (1..18).map { track("oth-$it", "Other Artist $it") }
        val store = FakeHistoryStore(WaveHistory())
        val engine = engine(
            store = store,
            candidates = repeat.map { Candidate(it, CandidateOrigin.RELATED, "s1") } +
                others.map { Candidate(it, CandidateOrigin.RELATED, it.id) },
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 20)
        val positions = result.tracks.mapIndexedNotNull { idx, t ->
            idx.takeIf { t.artist == "Repeat Artist" }
        }
        assertTrue(positions.size <= 2)
        if (positions.size == 2) {
            assertTrue("positions=${positions[0]},${positions[1]}", positions[1] - positions[0] >= 5)
        }
    }

    @Test
    fun `familiar tracks respect 30 day impression and 21 day play windows`() = runBlocking {
        val core = (1..18).map { track("core-$it", "Core Artist $it") }
        val recentShownFamiliar = track("fam-shown", "Fam Artist 1")
        val recentPlayedFamiliar = track("fam-played", "Fam Artist 2")
        val eligibleFamiliar = track("fam-ok", "Fam Artist 3")
        val history = WaveHistory(
            impressions = listOf(impression(recentShownFamiliar, now - 25 * DAY_MS)),
            plays = listOf(
                PlayedTrack(recentPlayedFamiliar, now - 15 * DAY_MS, 1f, false),
                PlayedTrack(eligibleFamiliar, now - 35 * DAY_MS, 1f, false),
            ),
        )
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                listOf(recentShownFamiliar, recentPlayedFamiliar, eligibleFamiliar).map {
                    Candidate(it, CandidateOrigin.FAMILIAR, it.id)
                },
        )

        val result = engine.nextBatch(plan.copy(history = history), WaveSessionState(), 20)
        val ids = result.tracks.map { it.id }.toSet()

        assertFalse("fam-shown" in ids)
        assertFalse("fam-played" in ids)
        assertTrue("fam-ok" in ids)
    }

    @Test
    fun `familiar mood targets fifteen percent familiar and ten percent exploration`() = runBlocking {
        val core = (1..20).map { track("core-$it", "Core $it") }
        val tags = (1..6).map { track("tag-$it", "Tag $it") }
        val familiar = (1..5).map { track("old-$it", "Old $it") }
        val history = WaveHistory(plays = familiar.map { PlayedTrack(it, now - 40 * DAY_MS, 1f, false) })
        val store = FakeHistoryStore(history)
        val engine = engine(
            store = store,
            candidates = core.map { Candidate(it, CandidateOrigin.RELATED, it.id) } +
                tags.map { Candidate(it, CandidateOrigin.TAG, it.id) } +
                familiar.map { Candidate(it, CandidateOrigin.FAMILIAR, it.id) },
        )

        val result = engine.nextBatch(
            plan.copy(history = history, mood = WaveMood.FAMILIAR),
            WaveSessionState(),
            20,
        )

        assertEquals(3, result.tracks.count { it.id.startsWith("old-") })
        val exploration = result.tracks.count { it.id.startsWith("tag-") }
        assertTrue("exploration=$exploration", exploration in 2..4)
    }

    @Test
    fun `source exception or timeout does not fail wave and shortage triggers retry`() = runBlocking {
        var calls = 0
        val failingSource = object : CandidateSource {
            override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
                throw IllegalStateException("boom")
            }
        }
        val slowSource = object : CandidateSource {
            override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
                delay(500)
                return listOf(Candidate(track("slow", "Slow Artist"), CandidateOrigin.RELATED, "slow"))
            }
        }
        val growingSource = object : CandidateSource {
            override suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate> {
                calls++
                return if (calls == 1) {
                    (1..4).map { Candidate(track("g1-$it", "Artist A$it"), CandidateOrigin.RELATED, "a$it") }
                } else {
                    (1..6).map { Candidate(track("g2-$it", "Artist B$it"), CandidateOrigin.RELATED, "b$it") }
                }
            }
        }
        val engine = WaveEngine(
            sources = setOf(failingSource, slowSource, growingSource),
            tasteProfileProvider = object : TasteProfileProvider {
                override suspend fun profile(): WaveTasteProfile = profile
            },
            historyStore = FakeHistoryStore(WaveHistory()),
            clock = object : Clock { override fun nowMs(): Long = now },
            randomProvider = object : RandomProvider {
                override fun newSeed(): Long = 7L
                override fun create(seed: Long): Random = Random(seed)
            },
            config = WaveConfig(candidateTimeoutMs = 50L),
        )

        val result = engine.nextBatch(plan, WaveSessionState(), 10)

        assertEquals(2, calls)
        assertEquals(10, result.tracks.size)
        assertFalse(result.isShort)
    }

    private fun engine(
        store: FakeHistoryStore,
        candidates: List<Candidate>,
        profile: WaveTasteProfile = this.profile,
    ): WaveEngine {
        val source = object : CandidateSource {
            override suspend fun candidates(
                plan: WaveSessionPlan,
                randomSeed: Long,
            ): List<Candidate> = candidates
        }
        return WaveEngine(
            sources = setOf(source),
            tasteProfileProvider = object : TasteProfileProvider {
                override suspend fun profile(): WaveTasteProfile = profile
            },
            historyStore = store,
            clock = object : Clock {
                override fun nowMs(): Long = now
            },
            randomProvider = object : RandomProvider {
                override fun newSeed(): Long = 7L
                override fun create(seed: Long): Random = Random(seed)
            },
            config = WaveConfig(candidateTimeoutMs = 1_000L),
        )
    }

    private fun track(
        id: String,
        artist: String,
        title: String = "Title $id",
        durationMs: Long = 180_000L,
        source: Source = Source.SOUNDCLOUD,
        subscription: Boolean = false,
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = "rock",
        transcodingUrl = "https://soundcloud.com/test/$id",
        isSubscriptionOnly = subscription,
    )

    private fun impression(track: Track, at: Long) = WaveImpression(
        trackKey = MusicNormalizer.trackKey(track.artist, track.title),
        trackId = track.id,
        artistKey = MusicNormalizer.normalizeArtist(track.artist),
        sessionId = "old-session",
        origin = CandidateOrigin.RELATED,
        seedKey = null,
        shownAt = at,
        outcome = WaveOutcome.SHOWN,
        playedMs = 0L,
    )

    private class FakeHistoryStore(var history: WaveHistory) : WaveHistoryStore {
        val shown = mutableListOf<Candidate>()
        val outcomes = mutableListOf<WaveOutcome>()

        override suspend fun loadHistory(nowMs: Long): WaveHistory = history

        override suspend fun seedTracks(sinceMs: Long): List<PlayedTrack> = history.plays

        override suspend fun previousSessionSeedKeys(limit: Int): Set<String> = emptySet()

        override suspend fun previousSessionSeedArtistKeys(limit: Int): Set<String> = emptySet()

        override suspend fun impressionFor(trackKey: String): WaveImpression? = null

        override suspend fun latestSession(): WaveSessionRecord? = null

        override suspend fun recordShown(sessionId: String, candidates: List<Candidate>) {
            shown += candidates
        }

        override suspend fun recordOutcome(
            sessionId: String?,
            track: Track,
            outcome: WaveOutcome,
            playedMs: Long,
        ) {
            outcomes += outcome
        }

        override suspend fun saveSession(record: WaveSessionRecord) = Unit

        override suspend fun cleanupIfNeeded(nowMs: Long) = Unit

        override suspend fun blockArtist(artistKey: String, nowMs: Long) = Unit
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
