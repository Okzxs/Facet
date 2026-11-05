package com.facet.music.core.network

/** Срок жизни подписанной ссылки из query-параметра expire/expires. */
object StreamExpiry {

    private val EXPIRES = Regex("""[?&](?:expire|expires)=(\d+)""", RegexOption.IGNORE_CASE)

    fun fromUrl(url: String): Long? =
        EXPIRES.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.times(1000L)
}
