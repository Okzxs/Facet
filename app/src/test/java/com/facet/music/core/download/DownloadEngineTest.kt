package com.facet.music.core.download

import androidx.media3.common.MimeTypes
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Поведенческие тесты движка скачивания:
 * протухший URL (403) требует свежего резолва, а сбой не должен
 * уничтожать уже скачанный файл.
 */
class DownloadEngineTest {

    private lateinit var server: TestHttpServer
    private lateinit var downloadsDir: File

    private val payload = "fresh-audio".toByteArray()

    @Before
    fun setUp() {
        downloadsDir = Files.createTempDirectory("download-engine-test").toFile()
        server = TestHttpServer()
    }

    @After
    fun tearDown() {
        server.stop()
        downloadsDir.deleteRecursively()
    }

    @Test
    fun `403 triggers fresh resolve and second download attempt`() = runBlocking {
        server.on("/expired", 403)
        server.on("/audio", 200, payload)

        var resolveCalls = 0
        val urls = ArrayDeque(listOf("${server.baseUrl}/expired", "${server.baseUrl}/audio"))
        var savedMediaUrl: String? = null
        var savedMime: String? = null

        val engine = engine(
            resolve = { track ->
                resolveCalls += 1
                Result.success(
                    ResolvedDownload(
                        track = track,
                        source = Source.SOUNDCLOUD,
                        streamUrl = urls.removeFirst(),
                        mimeType = MimeTypes.AUDIO_MPEG,
                    ),
                )
            },
            save = { _, mediaUrl, mime ->
                savedMediaUrl = mediaUrl
                savedMime = mime
            },
        )
        val track = track()

        val result = engine.download(track)

        assertTrue(result.isSuccess)
        assertEquals(2, resolveCalls)
        assertTrue(savedMediaUrl!!.startsWith("file:"))
        assertTrue(savedMediaUrl!!.endsWith(".mp3"))
        assertEquals(MimeTypes.AUDIO_MPEG, savedMime)
        assertArrayEquals(payload, targetFile(track).readBytes())
        assertNoPartFiles()
    }

    @Test
    fun `failed download keeps previous file and does not touch Room`() = runBlocking {
        server.on("/broken", 500)
        var saved = false
        val engine = engine(
            resolve = { track ->
                Result.success(
                    ResolvedDownload(
                        track = track,
                        source = Source.SOUNDCLOUD,
                        streamUrl = "${server.baseUrl}/broken",
                        mimeType = MimeTypes.AUDIO_MPEG,
                    ),
                )
            },
            save = { _, _, _ -> saved = true },
        )
        val track = track()
        val target = targetFile(track)
        target.writeText("old music")

        val result = engine.download(track)

        assertTrue(result.isFailure)
        assertEquals("old music", target.readText())
        assertFalse(saved)
        assertNoPartFiles()
    }

    @Test
    fun `repeated 403 fails after one retry and keeps previous file`() = runBlocking {
        server.on("/expired", 403)
        var resolveCalls = 0
        val engine = engine(
            resolve = { track ->
                resolveCalls += 1
                Result.success(
                    ResolvedDownload(
                        track = track,
                        source = Source.SOUNDCLOUD,
                        streamUrl = "${server.baseUrl}/expired",
                        mimeType = MimeTypes.AUDIO_MPEG,
                    ),
                )
            },
            save = { _, _, _ -> error("save не должен вызываться") },
        )
        val track = track()
        val target = targetFile(track)
        target.writeText("old music")

        val result = engine.download(track)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HttpStatusException)
        assertEquals(2, resolveCalls)
        assertEquals("old music", target.readText())
        assertNoPartFiles()
    }

    @Test
    fun `successful download replaces previous file only after full download`() = runBlocking {
        server.on("/audio", 200, payload)
        var saved = false
        val engine = engine(
            resolve = { track ->
                Result.success(
                    ResolvedDownload(
                        track = track,
                        source = Source.SOUNDCLOUD,
                        streamUrl = "${server.baseUrl}/audio",
                        mimeType = MimeTypes.AUDIO_MPEG,
                    ),
                )
            },
            save = { _, _, _ -> saved = true },
        )
        val track = track()
        val target = targetFile(track)
        target.writeText("old music")

        val result = engine.download(track)

        assertTrue(result.isSuccess)
        assertTrue(saved)
        assertArrayEquals(payload, target.readBytes())
        assertNoPartFiles()
    }

    private fun engine(
        resolve: suspend (Track) -> Result<ResolvedDownload>,
        save: suspend (Track, String, String) -> Unit,
    ) = DownloadEngine(
        downloadsDir = downloadsDir,
        client = OkHttpClient(),
        resolve = resolve,
        save = save,
        mediaUrl = { it.toURI().toString() },
        workId = "test-work",
    )

    private fun track() = Track(
        id = "https://soundcloud.com/artist/track",
        source = Source.SOUNDCLOUD,
        title = "Track",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = null,
        transcodingUrl = "https://soundcloud.com/artist/track",
    )

    private fun targetFile(track: Track): File = File(
        downloadsDir,
        DownloadTrackWorker.downloadFileName(track.id, "mp3"),
    )

    private fun assertNoPartFiles() {
        assertFalse(
            downloadsDir.listFiles().orEmpty().any { it.name.endsWith(".part") },
        )
    }

    /**
     * Минимальный HTTP-сервер для тестов: без com.sun.net.httpserver,
     * который не входит в classpath Android-юнит-тестов.
     */
    private class TestHttpServer {
        private data class Reply(val code: Int, val body: ByteArray)

        private val serverSocket = ServerSocket(
            0,
            0,
            InetAddress.getByName("127.0.0.1"),
        )
        private val replies = ConcurrentHashMap<String, Reply>()

        val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

        private val thread = Thread(::serve, "test-http-server").apply {
            isDaemon = true
            start()
        }

        fun on(path: String, code: Int, body: ByteArray = ByteArray(0)) {
            replies[path] = Reply(code, body)
        }

        private fun serve() {
            while (!serverSocket.isClosed) {
                try {
                    serverSocket.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use
                        // Дочитываем заголовки до пустой строки.
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }

                        val path = requestLine
                            .split(' ')
                            .getOrNull(1)
                            ?.substringBefore('?')
                            ?: "/"
                        val reply = replies[path] ?: Reply(404, ByteArray(0))

                        val header = buildString {
                            append("HTTP/1.1 ${reply.code} ${reason(reply.code)}\r\n")
                            append("Content-Length: ${reply.body.size}\r\n")
                            append("Content-Type: audio/mpeg\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                        }
                        socket.getOutputStream().use { output ->
                            output.write(header.toByteArray())
                            output.write(reply.body)
                            output.flush()
                        }
                    }
                } catch (e: IOException) {
                    if (serverSocket.isClosed) return
                }
            }
        }

        private fun reason(code: Int): String = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            500 -> "Internal Server Error"
            else -> "Not Found"
        }

        fun stop() {
            serverSocket.close()
            thread.interrupt()
        }
    }
}
