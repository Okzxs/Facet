package com.facet.music.core.source

import java.io.IOException

/**
 * Причина, по которой поток не получен. Нужна UI и политике повторов:
 * сетевые сбои можно повторить, «нет в регионе» и «только по подписке» — нет.
 */
sealed class ResolveError {
    data object Network : ResolveError()
    data object NotAvailable : ResolveError()
    data object SubscriptionOnly : ResolveError()
    data object GeoBlocked : ResolveError()
    data object NoMatch : ResolveError()
    data object Unknown : ResolveError()
}

/** Типизированная ошибка резолвера: сообщение не содержит ссылок и токенов. */
class ResolveException(
    val error: ResolveError,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: error.toString(), cause)
