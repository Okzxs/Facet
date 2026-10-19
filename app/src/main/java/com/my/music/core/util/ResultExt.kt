package com.my.music.core.util

import kotlinx.coroutines.CancellationException

/**
 * runCatching перехватывает CancellationException как обычную ошибку.
 * Этот хелпер пробрасывает отмену корутины дальше, чтобы scope корректно завершался.
 */
internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
    }
    return this
}
