package com.facet.music.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Блокирующий вызов OkHttp, который отменяется вместе с корутиной: Job
 * отменяет активный Call сразу, не дожидаясь таймаута. [runInterruptible]
 * дополнительно прерывает поток, если сеть игнорирует cancel().
 */
@OptIn(InternalCoroutinesApi::class)
internal suspend fun <T> OkHttpClient.executeCancellable(
    request: Request,
    block: (Response) -> T,
): T {
    val call = newCall(request)
    val job = currentCoroutineContext()[Job]
    val handle = job?.invokeOnCompletion(
        onCancelling = true,
        invokeImmediately = true,
    ) {
        call.cancel()
    }

    return try {
        runInterruptible(Dispatchers.IO) {
            call.execute().use(block)
        }
    } finally {
        handle?.dispose()
    }
}
