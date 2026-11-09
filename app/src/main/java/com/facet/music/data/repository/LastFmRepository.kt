package com.facet.music.data.repository

/**
 * Трек из Last.fm: только метаданные, без ссылки на поток.
 */
data class LastFmTrack(
    val name: String,
    val artist: String,
    val durationMs: Long = 0L,
    val listeners: Long = 0L,
)

/**
 * Last.fm: похожие артисты, топ-треки и теги.
 */
interface LastFmRepository {

    /**
     * Вернуть имена артистов, похожих на [artist].
     * Пустой список означает, что ключ не настроен или сервис ничего не нашёл.
     */
    suspend fun getSimilarArtists(artist: String, limit: Int = 8): Result<List<String>>

    suspend fun getArtistTopTracks(
        artist: String,
        limit: Int = 20,
        page: Int = 1,
    ): Result<List<LastFmTrack>>

    suspend fun getTagTopTracks(
        tag: String,
        limit: Int = 20,
        page: Int = 1,
    ): Result<List<LastFmTrack>>

    suspend fun getArtistTopTags(artist: String, limit: Int = 10): Result<List<String>>

    /** Глобальный чарт артистов — холодный старт волны. */
    suspend fun getTopArtists(limit: Int = 30): Result<List<String>>
}
