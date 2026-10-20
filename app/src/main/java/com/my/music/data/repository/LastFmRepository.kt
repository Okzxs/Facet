package com.my.music.data.repository

/**
 * Поиск похожих артистов через Last.fm.
 */
interface LastFmRepository {

    /**
     * Вернуть имена артистов, похожих на [artist].
     * Пустой список означает, что ключ не настроен или сервис ничего не нашёл.
     */
    suspend fun getSimilarArtists(artist: String, limit: Int = 8): Result<List<String>>
}
