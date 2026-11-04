package com.facet.music.core.download

import androidx.media3.common.MimeTypes
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.core.source.ResolvedStream
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlaybackSourceResolverTest {

    private val playbackResolver = mockk<PlaybackResolver>()

    @Test
    fun `download asks playback resolver for progressive stream`() = runTest {
        val track = track()
        coEvery { playbackResolver.resolve(track, true) } returns Result.success(
            ResolvedStream(
                url = "https://youtube.example/audio.m4a",
                mimeType = MimeTypes.AUDIO_MP4,
                isHls = false,
                source = Source.YOUTUBE,
            ),
        )

        val result = DownloadSourceResolver(playbackResolver).resolve(track)

        assertTrue(result.isSuccess)
        assertEquals(Source.YOUTUBE, result.getOrThrow().source)
        assertEquals("https://youtube.example/audio.m4a", result.getOrThrow().streamUrl)
        coVerify(exactly = 1) { playbackResolver.resolve(track, true) }
    }

    @Test
    fun `already downloaded file is not downloaded twice`() = runTest {
        val track = track()
        coEvery { playbackResolver.resolve(track, true) } returns Result.success(
            ResolvedStream(
                url = "file:///data/user/0/com.facet.music/files/downloads/a.mp3",
                mimeType = MimeTypes.AUDIO_MPEG,
                isHls = false,
                source = Source.SOUNDCLOUD,
            ),
        )

        val result = DownloadSourceResolver(playbackResolver).resolve(track)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DownloadSourceUnavailableException)
    }

    private fun track() = Track(
        id = "spotify:download",
        source = Source.SPOTIFY,
        title = "Track",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
    )
}
