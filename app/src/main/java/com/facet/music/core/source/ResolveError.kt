package com.facet.music.core.source

import java.io.IOException

sealed class ResolveError {
    data object Network : ResolveError()
    data object NotAvailable : ResolveError()
    data object SubscriptionOnly : ResolveError()
    data object GeoBlocked : ResolveError()
    data object NoMatch : ResolveError()
    data object Unknown : ResolveError()
}

class ResolveException(
    val error: ResolveError,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: error.toString(), cause)
