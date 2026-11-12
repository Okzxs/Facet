package com.facet.music.core.source

import androidx.media3.common.MimeTypes
import com.facet.music.data.local.dao.ResolvedMatchDao
import com.facet.music.data.local.entity.ResolvedMatchEntity
import com.facet.music.data.model.DownloadedTrack
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackResolverTest {

    @Test
    fun `metadata track matches soundcloud and stores match`() = runTest {
        val candidate = track(
            id = "https://soundcloud.com/a/track",
            source = Source.SOUNDCLOUD,
        )
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            searchResult = Result.success(listOf(candidate)),
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD)) },
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(any()) } returns null
        val saved = slot<ResolvedMatchEntity>()
        coEvery { dao.upsert(capture(saved)) } just Runs
        val resolver = resolver(listOf(soundcloud), dao)

        val result = resolver.resolve(track("spotify:1", Source.SPOTIFY))

        assertTrue(result.isSuccess)
        assertEquals(1, soundcloud.searchCalls.get())
        assertEquals(1, soundcloud.resolveCalls.get())
        assertEquals("https://soundcloud.com/a/track", saved.captured.matchedUrl)
        assertEquals("SOUNDCLOUD", saved.captured.matchedSource)
    }

    @Test
    fun `single flight shares one resolve between callers`() = runTest {
        val track = track("sc:1", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                delay(100)
                Result.success(stream(Source.SOUNDCLOUD))
            },
        )
        val resolver = resolver(listOf(soundcloud))

        val first = async { resolver.resolve(track) }
        val second = async { resolver.resolve(track) }
        advanceUntilIdle()

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, soundcloud.resolveCalls.get())
    }

    @Test
    fun `network failure is retried with backoff and then succeeds`() = runTest {
        val track = track("sc:2", Source.SOUNDCLOUD)
        val attempts = AtomicInteger()
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                if (attempts.incrementAndGet() < 3) {
                    Result.failure(IOException("timeout"))
                } else {
                    Result.success(stream(Source.SOUNDCLOUD))
                }
            },
        )
        val resolver = resolver(listOf(soundcloud))

        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `not available is not retried`() = runTest {
        val track = track("sc:3", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                Result.failure(
                    ResolveException(ResolveError.NotAvailable, "404"),
                )
            },
        )
        val resolver = resolver(listOf(soundcloud))

        val result = resolver.resolve(track)

        assertTrue(result.isFailure)
        assertEquals(1, soundcloud.resolveCalls.get())
    }

    @Test
    fun `stored match resolves without searching again`() = runTest {
        val original = track("spotify:2", Source.SPOTIFY)
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            resolveResult = { Result.success(stream(Source.YOUTUBE)) },
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(original.id) } returns ResolvedMatchEntity(
            originalTrackId = original.id,
            matchedSource = Source.YOUTUBE.name,
            matchedUrl = "https://youtube.com/watch?v=saved",
            confidence = 0.9,
            createdAt = 1L,
        )
        val resolver = resolver(listOf(youtube), dao)

        val result = resolver.resolve(original)

        assertTrue(result.isSuccess)
        assertEquals(0, youtube.searchCalls.get())
        assertEquals(1, youtube.resolveCalls.get())
    }

    @Test
    fun `progressive download skips hls match and takes youtube`() = runTest {
        val original = track("spotify:3", Source.SPOTIFY)
        val soundcloudCandidate = track(
            "https://soundcloud.com/hls",
            Source.SOUNDCLOUD,
            durationMs = 180_000L,
        )
        val youtubeCandidate = track(
            "https://youtube.com/watch?v=progressive",
            Source.YOUTUBE,
            durationMs = 184_000L,
        )
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            searchResult = Result.success(listOf(soundcloudCandidate)),
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD, isHls = true)) },
        )
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            searchResult = Result.success(listOf(youtubeCandidate)),
            resolveResult = { Result.success(stream(Source.YOUTUBE)) },
        )
        val resolver = resolver(listOf(soundcloud, youtube))

        val result = resolver.resolve(original, requireProgressive = true)

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
        assertEquals(1, soundcloud.resolveCalls.get())
        assertEquals(1, youtube.resolveCalls.get())
    }

    @Test
    fun `expired cached stream is resolved again`() = runTest {
        val track = track("sc:4", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                Result.success(
                    stream(Source.SOUNDCLOUD).copy(
                        expiresAtMs = System.currentTimeMillis() - 1,
                    ),
                )
            },
        )
        val resolver = resolver(listOf(soundcloud))

        resolver.resolve(track)
        resolver.resolve(track)

        assertEquals(2, soundcloud.resolveCalls.get())
    }

    @Test
    fun `downloaded track has highest priority before network providers`() = runTest {
        val track = track("sc:dl", Source.SOUNDCLOUD)
        val downloads = mockk<DownloadRepository>()
        coEvery { downloads.getDownloadedTrack(track.id) } returns DownloadedTrack(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = null,
            durationMs = track.durationMs,
            mediaUrl = "file:///music/offline.mp3",
            mimeType = MimeTypes.AUDIO_MPEG,
            downloadedAt = 1_000L,
        )
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD)) },
        )
        val resolver = PlaybackResolver(
            downloadRepository = downloads,
            registry = PlayableSourceRegistry(setOf(soundcloud)),
            matcher = TrackMatcher(),
            matchDao = mockk(relaxed = true),
            scope = this,
        )

        val result = resolver.resolve(track).getOrThrow()

        assertEquals("file:///music/offline.mp3", result.url)
        assertEquals(0, soundcloud.resolveCalls.get())
    }

    @Test
    fun `own provider skips room match and external search`() = runTest {
        val track = track("sc:own", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD)) },
        )
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            searchResult = Result.success(emptyList()),
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        val resolver = resolver(listOf(soundcloud, youtube), dao)

        val result = resolver.resolve(track)

        assertTrue(result.isSuccess)
        assertEquals(1, soundcloud.resolveCalls.get())
        assertEquals(0, youtube.searchCalls.get())
        coVerify(exactly = 0) { dao.get(any()) }
    }

    @Test
    fun `metadata only track never plays its own streamUrl or own provider`() = runTest {
        val previewUrl = "https://p.scdn.co/mp3-preview/legacy"
        val spotifyTrack = track("spotify:meta", Source.SPOTIFY).copy(streamUrl = previewUrl)
        val fakeSpotify = FakeSource(
            source = Source.SPOTIFY,
            resolveResult = {
                Result.success(stream(Source.SPOTIFY).copy(url = previewUrl))
            },
        )
        val youtubeCandidate = track("yt:meta", Source.YOUTUBE)
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            searchResult = Result.success(listOf(youtubeCandidate)),
            resolveResult = {
                Result.success(stream(Source.YOUTUBE).copy(url = "https://youtube.example/full.m4a"))
            },
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(any()) } returns null
        val resolver = resolver(listOf(fakeSpotify, youtube), dao)

        val result = resolver.resolve(spotifyTrack).getOrThrow()

        assertEquals(Source.YOUTUBE, result.source)
        assertEquals("https://youtube.example/full.m4a", result.url)
        assertEquals(0, fakeSpotify.resolveCalls.get())
    }

    @Test
    fun `invalidate clears cached stream so next resolve calls source again`() = runTest {
        val track = track("sc:inv", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD)) },
        )
        val resolver = resolver(listOf(soundcloud))

        resolver.resolve(track)
        resolver.resolve(track)
        assertEquals(1, soundcloud.resolveCalls.get())

        resolver.invalidate(track.id)
        resolver.resolve(track)
        assertEquals(2, soundcloud.resolveCalls.get())
    }

    @Test
    fun `failed stored match is deleted and triggers fresh search`() = runTest {
        val original = track("spotify:stale", Source.SPOTIFY)
        val freshCandidate = track("sc:fresh", Source.SOUNDCLOUD)
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(original.id) } returns ResolvedMatchEntity(
            originalTrackId = original.id,
            matchedSource = Source.YOUTUBE.name,
            matchedUrl = "https://youtube.com/watch?v=dead",
            confidence = 0.9,
            createdAt = 1L,
        )
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            resolveResult = {
                Result.failure(ResolveException(ResolveError.NotAvailable, "404"))
            },
        )
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            searchResult = Result.success(listOf(freshCandidate)),
            resolveResult = { Result.success(stream(Source.SOUNDCLOUD)) },
        )
        val resolver = resolver(listOf(youtube, soundcloud), dao)

        val result = resolver.resolve(original)

        assertTrue(result.isSuccess)
        assertEquals(Source.SOUNDCLOUD, result.getOrThrow().source)
        coVerify(exactly = 1) { dao.delete(original.id) }
        assertEquals(1, soundcloud.searchCalls.get())
    }

    @Test
    fun `uncertain match returns honest NoMatch error without resolving candidate`() = runTest {
        val original = track("spotify:nomatch", Source.SPOTIFY)
        val coverCandidate = track("yt:cover", Source.YOUTUBE).copy(
            title = "Track (Cover Version)",
        )
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            searchResult = Result.success(listOf(coverCandidate)),
            resolveResult = { Result.success(stream(Source.YOUTUBE)) },
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(any()) } returns null
        val resolver = resolver(listOf(youtube), dao)

        val result = resolver.resolve(original)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ResolveException
        assertEquals(ResolveError.NoMatch, error.error)
        assertEquals(0, youtube.resolveCalls.get())
    }

    @Test
    fun `transient network failure retries at most three times then fails`() = runTest {
        val track = track("sc:retry", Source.SOUNDCLOUD)
        val soundcloud = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = { Result.failure(IOException("timeout")) },
        )
        val resolver = resolver(listOf(soundcloud))

        val result = resolver.resolve(track)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ResolveException
        assertEquals(ResolveError.Network, error.error)
        assertEquals(4, soundcloud.resolveCalls.get())
    }

    @Test
    fun `subscription and geo errors are never retried`() = runTest {
        val subTrack = track("sc:sub", Source.SOUNDCLOUD)
        val geoTrack = track("sc:geo", Source.SOUNDCLOUD)
        val subSource = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                Result.failure(ResolveException(ResolveError.SubscriptionOnly, "Go+"))
            },
        )
        val geoSource = FakeSource(
            source = Source.SOUNDCLOUD,
            resolveResult = {
                Result.failure(ResolveException(ResolveError.GeoBlocked, "geo"))
            },
        )

        val subResult = resolver(listOf(subSource)).resolve(subTrack)
        val geoResult = resolver(listOf(geoSource)).resolve(geoTrack)

        assertEquals(ResolveError.SubscriptionOnly, (subResult.exceptionOrNull() as ResolveException).error)
        assertEquals(1, subSource.resolveCalls.get())
        assertEquals(ResolveError.GeoBlocked, (geoResult.exceptionOrNull() as ResolveException).error)
        assertEquals(1, geoSource.resolveCalls.get())
    }

    @Test
    fun `single flight shares external search and resolve for metadata track`() = runTest {
        val original = track("spotify:sf", Source.SPOTIFY)
        val candidate = track("yt:sf", Source.YOUTUBE)
        val youtube = FakeSource(
            source = Source.YOUTUBE,
            searchResult = Result.success(listOf(candidate)),
            resolveResult = {
                delay(100)
                Result.success(stream(Source.YOUTUBE))
            },
        )
        val dao = mockk<ResolvedMatchDao>(relaxed = true)
        coEvery { dao.get(any()) } returns null
        val resolver = resolver(listOf(youtube), dao)

        val first = async { resolver.resolve(original) }
        val second = async { resolver.resolve(original) }
        advanceUntilIdle()

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, youtube.searchCalls.get())
        assertEquals(1, youtube.resolveCalls.get())
    }

    private fun TestScope.resolver(
        sources: List<PlayableSource>,
        dao: ResolvedMatchDao = mockk(relaxed = true),
    ): PlaybackResolver {
        val downloads = mockk<DownloadRepository>()
        coEvery { downloads.getDownloadedTrack(any()) } returns null
        return PlaybackResolver(
            downloadRepository = downloads,
            registry = PlayableSourceRegistry(sources.toSet()),
            matcher = TrackMatcher(),
            matchDao = dao,
            scope = this,
        )
    }

    private fun stream(source: Source, isHls: Boolean = false): ResolvedStream =
        ResolvedStream(
            url = if (isHls) "https://example.com/stream.m3u8" else "https://example.com/stream.mp3",
            mimeType = if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.AUDIO_MPEG,
            isHls = isHls,
            expiresAtMs = System.currentTimeMillis() + 60_000,
            source = source,
        )

    private fun track(
        id: String,
        source: Source,
        durationMs: Long = 180_000L,
    ): Track = Track(
        id = id,
        source = source,
        title = "Track",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = null,
        transcodingUrl = null,
    )

    private class FakeSource(
        override val source: Source,
        private val searchResult: Result<List<Track>> = Result.success(emptyList()),
        private val resolveResult: suspend () -> Result<ResolvedStream> = {
            Result.failure(IOException("no stream"))
        },
    ) : PlayableSource {

        val searchCalls = AtomicInteger()
        val resolveCalls = AtomicInteger()

        override val capabilities = SourceCapabilities(fullStream = true)

        override suspend fun search(
            query: String,
            limit: Int,
            page: Int,
            useCache: Boolean,
        ): Result<List<Track>> {
            searchCalls.incrementAndGet()
            return searchResult
        }

        override suspend fun resolveStream(track: Track): Result<ResolvedStream> {
            resolveCalls.incrementAndGet()
            return resolveResult()
        }
    }
}
