package com.facet.music.core.util

import kotlinx.coroutines.CancellationException

internal fun <T> Result<T>.rethrowCancellation(): Result<T> {
    exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
    }
    return this
}
