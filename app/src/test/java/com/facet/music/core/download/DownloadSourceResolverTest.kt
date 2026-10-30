package com.facet.music.core.download

import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Резолвер должен качать SoundCloud progressive напрямую, отсекать HLS,
 * искать запасной вариант на YouTube и не принимать мусорные совпадения.
 */
class DownloadSourceResolverTest {

    private val recentlyFailed = RecentlyFailedSources()

    @Test
    fun `progressive soundcloud is downloaded without youtube`() = runTest {
        var youtubeSearchCalls = 0
        var youtubeResolveCalls = 0
        val soundCloud = source(
            source = Source.SOUNDCLOUD,
            capability = DownloadCapability.PROGRESSIVE,
            resolve = {
                Result.success(
                    AudioStreamResult(
                        url = "https://soundcloud.example/audio.mp3",
                        isHls = false,
                        mimeType = "audio/mpeg",
                    ),
                )
            },
        )
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                youtubeSearchCalls += 1
                Result.failure(IllegalStateException("YouTube не должен искать"))
            },
            resolve = {
                youtubeResolveCalls += 1
                Result.failure(IllegalStateException("YouTube не должен резолвить"))
            },
        )

        val result = resolver(soundCloud, youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Seed Song", "Seed Artist"))

        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertEquals(Source.SOUNDCLOUD, resolved.source)
        assertEquals("https://soundcloud.example/audio.mp3", resolved.streamUrl)
        assertEquals(0, youtubeSearchCalls)
        assertEquals(0, youtubeResolveCalls)
    }

    @Test
    fun `soundcloud without progressive stream falls back to youtube`() = runTest {
        var soundCloudResolveCalls = 0
        val soundCloud = source(
            source = Source.SOUNDCLOUD,
            capability = DownloadCapability.PROGRESSIVE,
            resolve = {
                soundCloudResolveCalls += 1
                Result.failure(IllegalStateException("У трека только HLS"))
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
                    AudioStreamResult("https://youtube.example/audio.m4a", isHls = false),
                )
            },
        )

        val result = resolver(soundCloud, youtube)
            .resolve(track("sc-1", Source.SOUNDCLOUD, "Seed Song", "Seed Artist"))

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
        assertEquals(1, soundCloudResolveCalls)
    }

    @Test
    fun `hls-only soundcloud source is skipped and youtube is used`() = runTest {
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
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
        assertEquals("https://youtube.example/audio.m4a", result.getOrThrow().streamUrl)
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
    fun `strict match rejects title substring`() {
        val seed = track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83")
        val candidate = track("yt-1", Source.YOUTUBE, "Midnight City Lights", "M83")

        assertFalse(DownloadSourceResolver.matchesTrack(seed, candidate))
    }

    @Test
    fun `strict match requires exact artist`() {
        val seed = track("sc-1", Source.SOUNDCLOUD, "Midnight City", "M83")
        val candidate = track("yt-1", Source.YOUTUBE, "Midnight City", "M83 Tribute Band")

        assertFalse(DownloadSourceResolver.matchesTrack(seed, candidate))
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
    fun `relaxed youtube match allows five second duration difference`() = runTest {
        val queries = mutableListOf<String>()
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { query, limit ->
                queries += query
                assertEquals(10, limit)
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "M83 - Midnight City (Live)",
                            artist = "M83",
                            durationMs = 185_000L,
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

        val result = resolver(youtube).resolve(
            track(
                id = "sc-1",
                source = Source.SOUNDCLOUD,
                title = "Midnight City (Official Audio)",
                artist = "M83",
                durationMs = 180_000L,
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
        assertEquals(2, queries.size)
        assertEquals("M83 Midnight City (Official Audio)", queries[0])
        assertEquals("M83 Midnight City", queries[1])
        assertFalse(queries[1].contains("official", ignoreCase = true))
    }

    @Test
    fun `relaxed youtube match rejects duration difference over fifteen seconds`() = runTest {
        var resolveCalls = 0
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "M83 - Midnight City (Live)",
                            artist = "M83",
                            durationMs = 200_000L,
                        ),
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

        val result = resolver(youtube).resolve(
            track(
                id = "sc-1",
                source = Source.SOUNDCLOUD,
                title = "Midnight City",
                artist = "M83",
                durationMs = 180_000L,
            ),
        )

        assertTrue(result.isFailure)
        assertEquals(0, resolveCalls)
    }

    @Test
    fun `relaxed youtube match requires artist in title or channel`() = runTest {
        var resolveCalls = 0
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "Midnight City (Live)",
                            artist = "Tribute Band",
                        ),
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
    fun `relaxed youtube match accepts artist in channel name`() = runTest {
        val youtube = source(
            source = Source.YOUTUBE,
            capability = DownloadCapability.PROGRESSIVE,
            search = { _, _ ->
                Result.success(
                    listOf(
                        track(
                            id = "yt-1",
                            source = Source.YOUTUBE,
                            title = "Midnight City (Live)",
                            artist = "M83 - Topic",
                            durationMs = 185_000L,
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

        val result = resolver(youtube).resolve(
            track(
                id = "sc-1",
                source = Source.SOUNDCLOUD,
                title = "Midnight City",
                artist = "M83",
                durationMs = 180_000L,
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
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

    @Test
    fun `source unavailable exception uses user-facing message`() {
        assertEquals(
            "Не удалось найти этот трек на YouTube для скачивания. " +
                "Возможно, он удален или доступен только в SoundCloud",
            DownloadSourceUnavailableException().message,
        )
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
        durationMs: Long = 180_000L,
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = null,
        transcodingUrl = "https://example.com/$id",
    )
}
