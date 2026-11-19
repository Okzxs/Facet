package com.facet.music.data.source.external

import com.facet.music.data.source.soundcloud.SoundCloudClientIdProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudLikesProviderTest {
    private val clientIds = mockk<SoundCloudClientIdProvider>()

    @Test fun `401 generic API response stays retryable API error`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            coEvery { clientIds.get() } returns "test-id"
            server.enqueue(MockResponse().setBody("""{"id":7,"likes_count":1}"""))
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
            val provider = SoundCloudLikesProvider(OkHttpClient(), Json,
                clientIds, server.url("/").toString())
            val error = provider.fetchLikes("user").exceptionOrNull()
            assertTrue(error is SoundCloudApiException)
            assertEquals(401, (error as SoundCloudApiException).code)
        } finally { server.shutdown() }
    }

    @Test fun `pagination follows next href once and respects limit`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            coEvery { clientIds.get() } returns "test-id"
            server.enqueue(MockResponse().setBody("""{"id":7,"likes_count":3}"""))
            server.enqueue(MockResponse().setBody(page("one", server.url("/page2").toString())))
            server.enqueue(MockResponse().setBody(page("two", server.url("/page2").toString())))
            val provider = SoundCloudLikesProvider(OkHttpClient(), Json,
                clientIds, server.url("/").toString())
            val result = provider.fetchLikes("user", limit = 2).getOrThrow()
            assertEquals(2, result.tracks.size)
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    private fun page(id: String, next: String) = """{"collection":[{"track":{"permalink_url":"https://soundcloud.com/a/$id","title":"$id"}}],"next_href":"$next"}"""
}
