package com.my.music.core.download

import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Резолвер должен отсекать HLS-источники, выбирать YouTube для
 * SoundCloud-трека и не принимать частичные совпадения названий.
 */
class DownloadSourceResolverTest {

    private val recentlyFailed = RecentlyFailedSources()

    @Test
    fun `soundcloud seed is filtered and youtube progressive is selected`() = runTest {
        var soundCloudSearchCalls = 0
        val soundCloud = source(
            source = Source.SOUNDCLOUD,
            capability = DownloadCapability.HLS_ONLY,
            search = { _, _ ->
                soundCloudSearchCalls += 1
                Result.failure(IllegalStateException("SoundCloud не должен искать"))
            },
        )
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(track("yt-1", Source.YOUTUBE, "Seed Song", "Seed Artist")),
                )
            },
            resolve = {
                Result.success(
                    AudioStreamResult(
                        url = "https://youtube.example/audio.m4a",
                        isHls = false,
                        mimeType = "audio/mp4",
                    ),
                )
            },
        )

        val result = resolver(soundCloud, youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Seed Song", "Seed Artist"))

        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertEquals(Source.YOUTUBE, resolved.source)
        assertEquals("https://youtube.example/audio.m4a", resolved.streamUrl)
        assertEquals(0, soundCloudSearchCalls)
    }

    @Test
    fun `current progressive source is used without search`() = runTest {
        var searchCalls = 0
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                searchCalls += 1
                Result.failure(IllegalStateException("Поиск не нужен"))
            },
            resolve = {
                Result.success(
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("yt-1", Source.YOUTUBE, "Song", "Artist"))

        assertTrue(result.isSuccess)
        assertEquals(0, searchCalls)
    }

    @Test
    fun `title substring is not a match`() = runTest {
        var resolveCalls = 0
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track("yt-wrong", Source.YOUTUBE, "Midnight City Lights", "M83"),
                        track("yt-live", Source.YOUTUBE, "Midnight City (Live)", "M83"),
                    ),
                )
            },
            resolve = {
                resolveCalls += 1
                Result.success(
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83"))

        assertTrue(result.isFailure)
        assertEquals(0, resolveCalls)
    }

    @Test
    fun `official audio and featuring notes are tolerated`() = runTest {
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "Midnight City (Official Audio) feat. Susanne Sundfor",
                            artist = "M83",
                        ),
                    ),
                )
            },
            resolve = {
                Result.success(
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83"))

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
    }

    @Test
    fun `youtube artist prefix in title is tolerated`() = runTest {
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "M83 - Midnight City (Official Audio)",
                            artist = "M83",
                        ),
                    ),
                )
            },
            resolve = {
                Result.success(
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `artist must match exactly`() = runTest {
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track("yt-1", Source.YOUTUBE, "Midnight City", "M83 Tribute Band"),
                    ),
                )
            },
            resolve = {
                Result.success(
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `recently failed source blocks repeated search until ttl expires`() = runTest {
        var searchCalls = 0
        var now = 1_000L
        recentlyFailed.clock = { now }
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                searchCalls += 1
                Result.failure(IllegalStateException("Сеть недоступна"))
            },
        )
        val resolver = resolver(youtube)
        val seed = track("sc-1", Source.SOUNDCLOUD, "Song", "Artist")

        assertTrue(resolver.resolve(seed).isFailure)
        assertTrue(resolver.resolve(seed).isFailure)
        assertEquals(1, searchCalls)

        now += RecentlyFailedSources.TTL_MS + 1
        assertTrue(resolver.resolve(seed).isFailure)
        assertEquals(2, searchCalls)
    }

    @Test
    fun `hls result is rejected`() = runTest {
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(track("yt-1", Source.YOUTUBE, "Song", "Artist")),
                )
            },
            resolve = {
                Result.success(
                    AudioStreamResult(
                        url = "https://youtube.example/audio.m3u8",
                        isHls = true,
                        mimeType = "application/x-mpegURL",
                    ),
                )
            },
        )

        val result = resolver(youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Song", "Artist"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `no progressive sources fails fast`() = runTest {
        val result = DownloadSourceResolver(emptySet(), recentlyFailed)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Song", "Artist"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DownloadSourceUnavailableException)
    }

    private fun resolver(vararg sources: DownloadSource): DownloadSourceResolver =
        DownloadSourceResolver(sources.toSet(), recentlyFailed)

    private fun source(
        source: Source,
        capability: DownloadCapability,
        search: suspend (String, Int) -> Result<List<Track>> = { _, _ ->
            Result.failure(IllegalStateException("Поиск не ожидался"))
        },
        resolve: suspend (Track) -> Result<AudioStreamResult> = {
            Result.failure(IllegalStateException("Resolve не ожидался"))
        },
    ): DownloadSource {
        val sourceValue = source
        val capabilityValue = capability
        val searchFn = search
        val resolveFn = resolve
        return object : DownloadSource {
            override val source: Source = sourceValue
            override val capability: DownloadCapability = capabilityValue

            override suspend fun search(query: String, limit: Int): Result<List<Track>> =
                searchFn.invoke(query, limit)

            override suspend fun resolve(track: Track): Result<AudioStreamResult> =
                resolveFn.invoke(track)
        }
    }

    private fun track(
        id: String,
        source: Source,
        title: String,
        artist: String,
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
        transcodingUrl = "https://example.com/$id",
    )
}
