package com.facet.music.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.facet.music.core.source.PlaybackResolver
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackLoadErrorPolicyTest {

    @Test
    fun `http 403 invalidates cache and resolver up to two times`() {
        val resolver = mockk<PlaybackResolver>(relaxed = true)
        val cache = mockk<PlaybackCache>(relaxed = true)
        val policy = PlaybackLoadErrorPolicy(resolver, cache)
        val dataSpec = DataSpec(PlaybackUri.forTrack("sc:403"))
        val exception = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            dataSpec,
            ByteArray(0),
        )
        val info = LoadErrorHandlingPolicy.LoadErrorInfo(
            androidx.media3.exoplayer.source.LoadEventInfo(0L, dataSpec, 0L),
            androidx.media3.exoplayer.source.MediaLoadData(C.DATA_TYPE_MEDIA),
            exception,
            1,
        )

        assertEquals(250L, policy.getRetryDelayMsFor(info))
        assertEquals(250L, policy.getRetryDelayMsFor(info))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info))

        verify(exactly = 2) { cache.remove("sc:403") }
        verify(exactly = 2) { resolver.invalidate("sc:403") }
    }

    @Test
    fun `non expired errors do not invalidate cache or resolver`() {
        val resolver = mockk<PlaybackResolver>(relaxed = true)
        val cache = mockk<PlaybackCache>(relaxed = true)
        val policy = PlaybackLoadErrorPolicy(resolver, cache)
        val dataSpec = DataSpec(Uri.parse("https://example.com/audio.mp3"))
        val info = LoadErrorHandlingPolicy.LoadErrorInfo(
            androidx.media3.exoplayer.source.LoadEventInfo(0L, dataSpec, 0L),
            androidx.media3.exoplayer.source.MediaLoadData(C.DATA_TYPE_MEDIA),
            IOException("timeout"),
            1,
        )

        val delay = policy.getRetryDelayMsFor(info)

        assertTrue(delay >= 0L)
        verify(exactly = 0) { cache.remove(any()) }
        verify(exactly = 0) { resolver.invalidate(any()) }
    }
}
