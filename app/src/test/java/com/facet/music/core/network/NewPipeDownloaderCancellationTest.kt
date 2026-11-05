package com.facet.music.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Регрессия Б6/5.3: отмена корутины во время зависшего запроса должна
 * отменять OkHttp Call, а не ждать readTimeout.
 */
class NewPipeDownloaderCancellationTest {

    @Test
    fun `отмена зависшего запроса завершается быстрее 500 мс`() = runBlocking {
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val serverThread = thread(isDaemon = true, name = "hanging-http") {
            try {
                server.accept().use {
                    accepted.countDown()
                    Thread.sleep(5_000)
                }
            } catch (_: Exception) {
            }
        }

        val downloader = NewPipeDownloader(
            OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build(),
        )
        val request = Request.newBuilder()
            .url("http://127.0.0.1:${server.localPort}/hang")
            .httpMethod("GET")
            .build()

        try {
            val job = launch(Dispatchers.Default) {
                downloader.withCancellation { downloader.execute(request) }
            }

            assertTrue("Сервер не получил запрос", accepted.await(5, TimeUnit.SECONDS))

            val startedAt = System.nanoTime()
            withTimeout(3_000) { job.cancelAndJoin() }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue("Отмена заняла ${elapsedMs} мс", elapsedMs < 500)
        } finally {
            runCatching { server.close() }
            serverThread.interrupt()
        }
    }
}
