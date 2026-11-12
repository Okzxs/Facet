package com.facet.music.core.download

import androidx.media3.common.MimeTypes
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class HttpStatusException(
    val code: Int,
    url: String,
) : IOException("HTTP $code при скачивании $url")

internal class DownloadException(message: String) : IOException(message)

internal class DownloadEngine(
    private val downloadsDir: File,
    private val client: OkHttpClient,
    private val resolve: suspend (Track) -> Result<ResolvedDownload>,
    private val save: suspend (Track, String, String) -> Unit,
    private val mediaUrl: (File) -> String,
    private val workId: String,
    private val onProgress: suspend (Float) -> Unit = {},
) {

    suspend fun download(track: Track): Result<Unit> {
        var canRetryExpiredUrl = true

        while (true) {
            val resolved = resolve(track).getOrElse { return Result.failure(it) }
            val mimeType = DownloadTrackWorker.mimeTypeFor(
                source = resolved.source,
                result = AudioStreamResult(
                    url = resolved.streamUrl,
                    isHls = false,
                    mimeType = resolved.mimeType,
                ),
            )
            if (mimeType == MimeTypes.APPLICATION_M3U8) {
                return Result.failure(DownloadException(DownloadTrackWorker.ERROR_ONLINE_ONLY))
            }

            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                return Result.failure(DownloadException("downloads_dir_unavailable"))
            }

            val extension = DownloadTrackWorker.extensionFor(mimeType, resolved.source)
            val targetFile = File(
                downloadsDir,
                DownloadTrackWorker.downloadFileName(track.id, extension),
            )
            val tempFile = File(downloadsDir, "${targetFile.name}.$workId.part")

            try {
                val bytes = downloadToFile(resolved.streamUrl, tempFile)
                if (bytes == 0L) throw DownloadException("empty_file")
                moveAtomically(tempFile, targetFile)
                if (mimeType == MimeTypes.AUDIO_MPEG) {
                    runCatching { applyId3Tags(track, targetFile) }
                }
            } catch (e: CancellationException) {
                tempFile.delete()
                throw e
            } catch (e: HttpStatusException) {
                tempFile.delete()
                if (canRetryExpiredUrl && e.code in EXPIRED_URL_CODES) {
                    canRetryExpiredUrl = false
                    continue
                }
                return Result.failure(e)
            } catch (e: Exception) {
                tempFile.delete()
                return Result.failure(e)
            }

            try {
                currentCoroutineContext().ensureActive()
                save(track, mediaUrl(targetFile), mimeType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure(e)
            }

            onProgress(1f)
            return Result.success(Unit)
        }
    }

    private fun applyId3Tags(track: Track, file: File) {
        val artwork = track.artworkUrl?.let { url ->
            runCatching { fetchArtwork(url) }.getOrNull()
        }
        Id3Writer.write(
            file = file,
            title = track.title,
            artist = track.artist,
            artwork = artwork,
            artworkMime = artwork?.let(Id3Writer::artworkMimeType) ?: "image/jpeg",
        )
    }

    private fun fetchArtwork(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Facet/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_ARTWORK_BYTES) return null
            return body.bytes()
        }
    }

    private suspend fun downloadToFile(url: String, targetFile: File): Long {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val call = client.newCall(request)
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException(response.code, url)
                }
                val body = response.body ?: throw IOException("Пустой ответ для $url")
                val expectedBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            if (expectedBytes > 0L) {
                                val percent = (downloadedBytes * 100 / expectedBytes).toInt()
                                if (percent >= lastPercent + PROGRESS_STEP || percent == 100) {
                                    lastPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }

                if (expectedBytes > 0L && downloadedBytes != expectedBytes) {
                    throw IOException(
                        "Размер файла не совпал: ожидалось $expectedBytes, " +
                            "получено $downloadedBytes",
                    )
                }
                return downloadedBytes
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun moveAtomically(source: File, target: File) {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        try {
            Files.move(
                sourcePath,
                targetPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: FileAlreadyExistsException) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val USER_AGENT = "FacetApp/1.0"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP = 5

        private const val MAX_ARTWORK_BYTES = 3L * 1024 * 1024

        private val EXPIRED_URL_CODES = setOf(403, 410)
    }
}
