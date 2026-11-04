package com.facet.music.core.source

import androidx.media3.common.MimeTypes
import com.facet.music.data.local.dao.ResolvedMatchDao
import com.facet.music.data.local.entity.ResolvedMatchEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.DownloadRepository
import io.mockk.Runs
import io.mockk.coEvery
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
