package com.facet.music.data.source.soundcloud

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoundCloudApiTest {

    private lateinit var server: MockWebServer
    private lateinit var clientIds: SoundCloudClientIdProvider
    private lateinit var api: SoundCloudApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        clientIds = mockk(relaxed = true)
        coEvery { clientIds.get() } returns "cid_initial"
        api = SoundCloudApi(
            client = OkHttpClient(),
            clientIds = clientIds,
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `prefers progressive mp3 over hls and ignores encrypted and snipped`() = runTest {
        val progressiveUrl = server.url("/media/prog").toString()
        val hlsUrl = server.url("/media/hls").toString()
        val encryptedUrl = server.url("/media/enc").toString()
        val snippedUrl = server.url("/media/snip").toString()

        server.enqueue(
            MockResponse().setBody(
                trackPayload(
                    transcoding(encryptedUrl, "ctr-encrypted-hls", "audio/mp4", "aac_160k"),
                    transcoding(snippedUrl, "progressive", "audio/mpeg", "mp3_128", snipped = true),
                    transcoding(hlsUrl, "hls", "audio/mpegurl", "abr_sq"),
                    transcoding(progressiveUrl, "progressive", "audio/mpeg", "mp3_128"),
                ),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"url":"https://cf-media.sndcdn.com/full.mp3"}"""))

        val stream = api.resolveStream(
            pageUrl = "https://soundcloud.com/artist/track",
            numericId = "12345",
            requireProgressive = false,
        )

        assertFalse(stream.isHls)
        assertEquals("audio/mpeg", stream.mimeType)
        assertEquals("https://cf-media.sndcdn.com/full.mp3", stream.url)

        val trackReq = server.takeRequest()
        assertTrue(trackReq.path!!.startsWith("/tracks/12345"))
        val mediaReq = server.takeRequest()
        assertTrue(mediaReq.path!!.startsWith("/media/prog"))
        assertTrue(mediaReq.path!!.contains("client_id=cid_initial"))
        assertTrue(mediaReq.path!!.contains("track_authorization=auth_tok"))
    }

    @Test
    fun `prefers hls mp3 over hls opus and falls back on 404 transcoding`() = runTest {
        val hlsMp3Url = server.url("/media/hls-mp3").toString()
        val hlsOpusUrl = server.url("/media/hls-opus").toString()

        server.enqueue(
            MockResponse().setBody(
                trackPayload(
                    transcoding(hlsOpusUrl, "hls", "audio/mp4; codecs=\"mp4a.40.2\"", "aac_160k"),
                    transcoding(hlsMp3Url, "hls", "audio/mpegurl", "abr_sq"),
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"url":"https://cf-hls-opus.sndcdn.com/playlist.m3u8"}"""))

        val stream = api.resolveStream(
            pageUrl = "https://soundcloud.com/artist/track",
            numericId = "12345",
            requireProgressive = false,
        )

        assertTrue(stream.isHls)
        assertEquals("audio/mp4", stream.mimeType)
        assertEquals("https://cf-hls-opus.sndcdn.com/playlist.m3u8", stream.url)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `invalidates client_id on 401 and retries once with fresh client_id`() = runTest {
        val progressiveUrl = server.url("/media/prog").toString()
        coEvery { clientIds.get() } returnsMany listOf("expired_cid", "fresh_cid")

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse().setBody(
                trackPayload(
                    transcoding(progressiveUrl, "progressive", "audio/mpeg", "mp3_128"),
                ),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"url":"https://cf-media.sndcdn.com/ok.mp3"}"""))

        val stream = api.resolveStream(
            pageUrl = "https://soundcloud.com/artist/track",
            numericId = "12345",
            requireProgressive = true,
        )

        assertEquals("https://cf-media.sndcdn.com/ok.mp3", stream.url)
        coVerify(exactly = 1) { clientIds.invalidate() }
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertTrue(first.path!!.contains("client_id=expired_cid"))
        assertTrue(second.path!!.contains("client_id=fresh_cid"))
    }

    @Test
    fun `401 retries only once and fails on second 401`() = runTest {
        coEvery { clientIds.get() } returnsMany listOf("cid_1", "cid_2")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val ex = assertThrows(SoundCloudException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.resolveStream(
                    pageUrl = "https://soundcloud.com/artist/track",
                    numericId = "12345",
                    requireProgressive = false,
                )
            }
        }

        assertEquals(SoundCloudFailure.HTTP, ex.failure)
        assertEquals(401, ex.code)
        assertEquals(2, server.requestCount)
        coVerify(exactly = 1) { clientIds.invalidate() }
    }

    @Test
    fun `only snipped transcodings throw SubscriptionOnly without fetching media`() = runTest {
        val snippedUrl = server.url("/media/snip").toString()
        server.enqueue(
            MockResponse().setBody(
                trackPayload(
                    transcoding(snippedUrl, "progressive", "audio/mpeg", "mp3_128", snipped = true),
                    transcoding(snippedUrl, "hls", "audio/mpegurl", "abr_sq", snipped = true),
                ),
            ),
        )

        val ex = assertThrows(SoundCloudException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.resolveStream(
                    pageUrl = "https://soundcloud.com/artist/track",
                    numericId = "12345",
                    requireProgressive = false,
                )
            }
        }

        assertEquals(SoundCloudFailure.SUBSCRIPTION_ONLY, ex.failure)
        assertEquals(1, server.requestCount)
    }

    private fun transcoding(
        url: String,
        protocol: String,
        mime: String,
        preset: String,
        snipped: Boolean = false,
    ): String = """
        {
          "url": "$url",
          "preset": "$preset",
          "snipped": $snipped,
          "format": {"protocol": "$protocol", "mime_type": "${mime.replace("\"", "\\\"")}"}
        }
    """.trimIndent()

    private fun trackPayload(vararg transcodings: String): String = """
        {
          "kind": "track",
          "id": 12345,
          "permalink_url": "https://soundcloud.com/artist/track",
          "title": "Track",
          "duration": 180000,
          "full_duration": 180000,
          "policy": "ALLOW",
          "monetization_model": "NOT_APPLICABLE",
          "track_authorization": "auth_tok",
          "user": {"username": "Artist"},
          "media": {"transcodings": [${transcodings.joinToString(",")}]}
        }
    """.trimIndent()
}
