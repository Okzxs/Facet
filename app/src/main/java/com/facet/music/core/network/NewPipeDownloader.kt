package com.facet.music.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeDownloader @Inject constructor(
    @MediaClient private val okHttpClient: OkHttpClient,
) : Downloader() {

    private class CancellationScope {
        val calls: MutableSet<Call> = ConcurrentHashMap.newKeySet()

        @Volatile
        var cancelled: Boolean = false
    }

    private val currentScope = ThreadLocal<CancellationScope?>()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()

        val requestBuilder = okhttp3.Request.Builder().url(url)

        var hasUserAgent = false
        request.headers().forEach { (key, values) ->
            if (key.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            values.forEachIndexed { index, value ->
                if (index == 0) {
                    requestBuilder.header(key, value)
                } else {
                    requestBuilder.addHeader(key, value)
                }
            }
        }
        if (!hasUserAgent) {
            requestBuilder.header("User-Agent", CHROME_USER_AGENT)
        }

        when (httpMethod) {
            "POST" -> {
                val body = (request.dataToSend() ?: ByteArray(0)).toRequestBody()
                requestBuilder.post(body)
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        val scope = currentScope.get()
        if (scope != null) {
            scope.calls.add(call)
            if (scope.cancelled) call.cancel()
        }

        try {
            val response = call.execute()

            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("429 Too Many Requests от SoundCloud", url)
            }

            val responseBody = response.body?.string() ?: ""

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString(),
            )
        } finally {
            scope?.calls?.remove(call)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun <T> withCancellation(block: () -> T): T {
        val scope = CancellationScope()
        val job = currentCoroutineContext()[Job]
        val cancelHandle = job?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) {
            scope.cancelled = true
            scope.calls.forEach { it.cancel() }
        }

        try {
            currentCoroutineContext().ensureActive()
            return runInterruptible(Dispatchers.IO) {
                val previous = currentScope.get()
                currentScope.set(scope)
                try {
                    block()
                } finally {
                    if (previous == null) currentScope.remove() else currentScope.set(previous)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            cancelHandle?.dispose()
        }
    }

    private companion object {
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
