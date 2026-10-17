package com.my.music.core.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-реализация [Downloader] для NewPipe Extractor.
 * NewPipe не умеет делать запросы сам — нужно предоставить реализацию.
 */
@Singleton
class NewPipeDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )

        // Прокидываем заголовки от NewPipe (User-Agent, Accept и т.д.)
        request.headers().forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        when (httpMethod) {
            "POST" -> {
                val body = (request.dataToSend() ?: ByteArray(0)).toRequestBody()
                requestBuilder.post(body)
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()

        // NewPipe интерпретирует 429 как капчу — бросаем нужное исключение
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
    }
}
