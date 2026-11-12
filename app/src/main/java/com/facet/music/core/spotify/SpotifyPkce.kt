package com.facet.music.core.spotify

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

object SpotifyPkce {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    const val VERIFIER_LENGTH: Int = 64

    const val STATE_LENGTH: Int = 24

    fun generateVerifier(random: Random = Random.Default): String =
        randomToken(VERIFIER_LENGTH, random)

    fun generateState(random: Random = Random.Default): String =
        randomToken(STATE_LENGTH, random)

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomToken(length: Int, random: Random): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
