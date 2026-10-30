package com.my.music.core.spotify

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

/**
 * PKCE для OAuth-входа Spotify (RFC 7636).
 *
 * Позволяет войти без `client_secret`: приложение генерирует случайный
 * `code_verifier`, отправляет провайдеру его SHA-256 (`code_challenge`),
 * а на обмен кода предъявляет сам verifier. Перехваченный код без verifier
 * бесполезен, поэтому секрет в APK не нужен.
 */
object SpotifyPkce {

    /** Допустимые символы verifier по RFC 7636: ALPHA / DIGIT / "-" / "." / "_" / "~". */
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** Длина verifier: Spotify принимает 43–128 символов. */
    const val VERIFIER_LENGTH: Int = 64

    /** Длина `state` — произвольная, 24 символов достаточно. */
    const val STATE_LENGTH: Int = 24

    /** Случайный `code_verifier` из разрешённого алфавита. */
    fun generateVerifier(random: Random = Random.Default): String =
        randomToken(VERIFIER_LENGTH, random)

    /** Случайный `state` для проверки, что ответ пришёл на наш запрос. */
    fun generateState(random: Random = Random.Default): String =
        randomToken(STATE_LENGTH, random)

    /** `code_challenge` = BASE64URL-без-паддинга(SHA-256(verifier)). */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomToken(length: Int, random: Random): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
