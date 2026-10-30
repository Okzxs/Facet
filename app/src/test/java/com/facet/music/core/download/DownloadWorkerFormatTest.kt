package com.facet.music.core.download

import androidx.media3.common.MimeTypes
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadWorkerFormatTest {

    @Test
    fun `youtube m4a stream gets m4a file extension`() {
        val mimeType = DownloadTrackWorker.mimeTypeFor(
            source = Source.YOUTUBE,
            result = AudioStreamResult(
                url = "https://example.com/audio",
                isHls = false,
                mimeType = MimeTypes.AUDIO_MP4,
            ),
        )

        assertEquals(MimeTypes.AUDIO_MP4, mimeType)
        assertEquals("m4a", DownloadTrackWorker.extensionFor(mimeType, Source.YOUTUBE))
    }

    @Test
    fun `soundcloud mp3 stream gets mp3 file extension`() {
        val mimeType = DownloadTrackWorker.mimeTypeFor(
            source = Source.SOUNDCLOUD,
            result = AudioStreamResult(
                url = "https://example.com/audio",
                isHls = false,
                mimeType = MimeTypes.AUDIO_MPEG,
            ),
        )

        assertEquals(MimeTypes.AUDIO_MPEG, mimeType)
        assertEquals("mp3", DownloadTrackWorker.extensionFor(mimeType, Source.SOUNDCLOUD))
    }

    @Test
    fun `youtube webm fallback keeps webm container`() {
        assertEquals(
            "webm",
            DownloadTrackWorker.extensionFor(MimeTypes.AUDIO_WEBM, Source.YOUTUBE),
        )
    }

    @Test
    fun `missing mime falls back by source`() {
        assertEquals(
            MimeTypes.AUDIO_MP4,
            DownloadTrackWorker.mimeTypeFor(
                Source.YOUTUBE,
                AudioStreamResult(url = "https://example.com/audio", isHls = false),
            ),
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            DownloadTrackWorker.mimeTypeFor(
                Source.SOUNDCLOUD,
                AudioStreamResult(url = "https://example.com/audio", isHls = false),
            ),
        )
    }

    @Test
    fun `file name uses requested extension and sanitizes it`() {
        val name = DownloadTrackWorker.downloadFileName(
            trackId = "https://soundcloud.com/artist/track",
            extension = "m4a",
        )
        val unsafeName = DownloadTrackWorker.downloadFileName(
            trackId = "https://soundcloud.com/artist/track",
            extension = "../mp3",
        )

        assertTrue(name.endsWith(".m4a"))
        assertTrue(unsafeName.endsWith(".mp3"))
    }
}
