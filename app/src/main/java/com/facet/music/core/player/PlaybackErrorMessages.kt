package com.facet.music.core.player

import androidx.media3.common.PlaybackException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Понятные пользователю сообщения вместо сырых Java-исключений. */
const val ERROR_NO_INTERNET = "Нет подключения к интернету. Проверьте сеть."
const val ERROR_TIMEOUT = "Превышено время ожидания. Попробуйте снова."
const val ERROR_SECURE_CONNECTION = "Ошибка защищённого соединения. Попробуйте снова."
const val ERROR_PLAYBACK = "Не удалось воспроизвести трек. Попробуйте снова."
const val ERROR_LYRICS = "Не удалось загрузить текст. Попробуйте снова."

/**
 * Сводит цепочку причин исключения к одному безопасному сообщению.
 * Сырой [Throwable.message] никогда не возвращается как есть.
 */
fun Throwable.toFriendlyPlaybackMessage(fallback: String = ERROR_PLAYBACK): String {
    val chain = generateSequence(this) { it.cause }.take(8).toList()
    val messages = chain.mapNotNull { it.message?.lowercase() }

    val noInternet = chain.any { it is UnknownHostException || it is ConnectException } ||
        messages.any {
            "unable to resolve host" in it ||
                "no address associated with hostname" in it ||
                "network is unreachable" in it ||
                "failed to connect" in it
        }
    if (noInternet) return ERROR_NO_INTERNET

    val timeout = chain.any { it is SocketTimeoutException } ||
        messages.any { "timeout" in it || "timed out" in it }
    if (timeout) return ERROR_TIMEOUT

    if (chain.any { it is SSLException }) return ERROR_SECURE_CONNECTION

    if (chain.any { it is SocketException }) return ERROR_NO_INTERNET

    return fallback
}

/**
 * Ошибки ExoPlayer: сначала смотрим на код (надёжнее для сетевых сбоев),
 * затем — на цепочку причин.
 */
fun PlaybackException.toFriendlyPlaybackMessage(): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> ERROR_TIMEOUT
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> ERROR_NO_INTERNET
    else -> cause?.toFriendlyPlaybackMessage() ?: ERROR_PLAYBACK
}
