package com.facet.music.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.InterruptedIOException

/**
 * Повторы только для GET: 429 и 503 — два раза, остальные 5xx — один.
 * Retry-After уважается, но не больше 10 секунд; иначе экспоненциальный
 * backoff с jitter.
 */
internal class RetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) return chain.proceed(request)

        var attempt = 0
        var response = chain.proceed(request)
        while (true) {
            val limit = RetryPolicy.retryLimit(response.code)
            if (attempt >= limit) return response

            val delay = RetryPolicy.delayMs(
                code = response.code,
                retryAfter = response.header("Retry-After"),
                attempt = attempt,
            )
            val code = response.code
            response.close()
            if (!sleep(delay)) {
                throw InterruptedIOException("Повтор прерван отменой запроса")
            }
            attempt++
            Log.d(TAG, "Повтор GET после HTTP $code: $attempt/$limit")
            response = chain.proceed(request)
        }
    }

    private fun sleep(ms: Long): Boolean = try {
        Thread.sleep(ms)
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private companion object {
        const val TAG = "RetryInterceptor"
    }
}
