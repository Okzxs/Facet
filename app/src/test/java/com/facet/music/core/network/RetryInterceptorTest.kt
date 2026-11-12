package com.facet.music.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries GET on 429 up to two times and returns 200`() {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(Request.Builder().url(server.url("/test")).get().build()).execute()

        assertEquals(200, response.code)
        assertEquals("ok", response.body?.string())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `stops retrying GET 503 after two retries`() {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(503))
        }

        val response = client.newCall(Request.Builder().url(server.url("/503")).get().build()).execute()

        assertEquals(503, response.code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `retries GET 500 only once`() {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(500))
        }

        val response = client.newCall(Request.Builder().url(server.url("/500")).get().build()).execute()

        assertEquals(500, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry GET 404 or POST 503`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))

        val get404 = client.newCall(Request.Builder().url(server.url("/missing")).get().build()).execute()
        val post503 = client.newCall(
            Request.Builder().url(server.url("/submit")).post("{}".toRequestBody()).build(),
        ).execute()

        assertEquals(404, get404.code)
        assertEquals(503, post503.code)
        assertEquals(2, server.requestCount)
    }
}
